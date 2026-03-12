import argparse
import json
from collections import OrderedDict
from pathlib import Path

import geopandas as gpd
import osmium
from pyproj import Geod
from shapely.geometry import LineString


ALLOWED_HIGHWAY_VALUES = {"path", "footway", "steps"}
EXCLUDED_HIGHWAY_VALUES = {"service", "track", "cycleway"}
EXCLUDED_FOOTWAY_VALUES = {"sidewalk", "crossing"}


def get_project_root() -> Path:
    """
    현재 파일 위치 기준으로 backend/python 루트 경로 계산
    현재 파일:
    backend/python/scripts/a_collect/extract_trails_from_pbf.py
    """
    return Path(__file__).resolve().parents[2]


def get_default_input_path() -> Path:
    """
    기본 입력 파일 경로
    """
    return get_project_root() / "data" / "raw" / "south-korea-latest.osm.pbf"


def get_default_output_path() -> Path:
    """
    기본 출력 파일 경로
    """
    return get_project_root() / "data" / "interim" / "a_output" / "standard_trail.geojson"


def to_plain_dict(tags) -> dict:
    """
    osmium의 TagList를 일반 dict로 변환
    """
    return {tag.k: tag.v for tag in tags}


def has_hiking_signal(tags_dict: dict) -> bool:
    """
    등산로/산길로 볼 만한 힌트가 있는지 판단
    """
    hiking_related_keys = [
        "sac_scale",
        "trail_visibility",
        "via_ferrata_scale",
        "mtb:scale:uphill",
        "incline",
    ]

    hiking_related_surfaces = {
        "ground",
        "dirt",
        "earth",
        "mud",
        "gravel",
        "fine_gravel",
        "rock",
        "pebblestone",
        "sand",
        "woodchips",
        "unpaved",
        "compacted",
    }

    hiking_route_values = {"hiking", "foot"}

    if tags_dict.get("route") in hiking_route_values:
        return True

    for key in hiking_related_keys:
        if tags_dict.get(key):
            return True

    surface = tags_dict.get("surface")
    if surface in hiking_related_surfaces:
        return True

    if tags_dict.get("foot") in {"yes", "designated", "permissive"} and tags_dict.get("bicycle") in {None, "no"}:
        if tags_dict.get("highway") in {"path", "steps"}:
            return True

    return False


def is_obviously_urban_walkway(tags_dict: dict) -> bool:
    """
    명백히 생활 보행로/도시 보행로처럼 보이는 패턴 제외
    """
    highway = tags_dict.get("highway")
    footway = tags_dict.get("footway")
    surface = tags_dict.get("surface")
    name = tags_dict.get("name")

    if highway in EXCLUDED_HIGHWAY_VALUES:
        return True

    if highway == "footway" and footway in EXCLUDED_FOOTWAY_VALUES:
        return True

    if footway == "sidewalk":
        return True

    # 도시형 포장 보행로 느낌이 강한 경우
    if highway == "footway":
        if surface in {"paved", "asphalt", "concrete", "paving_stones", "concrete:plates", "cobblestone"}:
            if not has_hiking_signal(tags_dict):
                return True

    # 일반 도로명/길 이름처럼 보이는 것 중 산길 힌트가 전혀 없는 경우
    if name:
        urban_name_keywords = ["로", "길", "거리", "대로", "street", "road"]
        if any(keyword in name.lower() for keyword in urban_name_keywords):
            if not has_hiking_signal(tags_dict):
                return True

    # 보행/자전거 겸용 생활 통로 느낌이 강한 경우
    if tags_dict.get("segregated") == "yes" and not has_hiking_signal(tags_dict):
        return True

    return False


def is_hiking_trail(tags_dict: dict) -> bool:
    """
    등산로/하이킹 관련 way만 추출하기 위한 필터
    기존보다 더 보수적으로 판단
    """
    highway = tags_dict.get("highway")
    route = tags_dict.get("route")
    sac_scale = tags_dict.get("sac_scale")

    if highway in EXCLUDED_HIGHWAY_VALUES:
        return False

    if is_obviously_urban_walkway(tags_dict):
        return False

    # 가장 강한 힌트
    if route == "hiking":
        return True

    if sac_scale is not None:
        return True

    # path는 기본 포함하되, 너무 도시형인 경우는 위에서 걸러짐
    if highway == "path":
        return True

    # footway/steps는 산길 힌트가 있을 때만 포함
    if highway in {"footway", "steps"}:
        return has_hiking_signal(tags_dict)

    return False


class HikingTrailWayHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.geod = Geod(ellps="WGS84")

    def way(self, w):
        tags_dict = to_plain_dict(w.tags)

        # 등산로 후보가 아니면 스킵
        if not is_hiking_trail(tags_dict):
            return

        coords = []
        for node in w.nodes:
            # 위치정보 없는 노드는 제외
            if not node.location.valid():
                continue

            lon = node.location.lon
            lat = node.location.lat
            coords.append((lon, lat))

        # 선을 만들 수 없는 경우 제외
        if len(coords) < 2:
            return

        try:
            line = LineString(coords)
        except Exception:
            return

        # 길이가 0이거나 비정상이면 제외
        if not line.is_valid or line.is_empty:
            return

        try:
            length_m = float(self.geod.geometry_length(line))
        except Exception:
            return

        if length_m <= 0:
            return

        row = {
            "osm_way_id": int(w.id),
            "name": tags_dict.get("name"),
            "source": "OSM",
            "source_ref": f"way/{w.id}",
            "length_m": round(length_m, 2),
            "surface": tags_dict.get("surface"),
            "trail_type": "trail",
            "mountain_name": None,
            "admin_region": None,
            "is_official": False,
            "raw_tags": tags_dict,
            "geometry": line,
        }
        self.rows.append(row)


def deduplicate_by_source_ref(rows):
    """
    같은 way가 중복으로 들어오는 상황 방지
    """
    unique = OrderedDict()
    for row in rows:
        unique[row["source_ref"]] = row
    return list(unique.values())


def assign_trail_ids(rows):
    """
    T0001, T0002 ... 순번 부여
    """
    for idx, row in enumerate(rows, start=1):
        row["trail_id"] = f"T{idx:04d}"
    return rows


def build_geodataframe(rows) -> gpd.GeoDataFrame:
    """
    최종 스키마 순서로 GeoDataFrame 생성
    """
    gdf = gpd.GeoDataFrame(rows, geometry="geometry", crs="EPSG:4326")

    final_columns = [
        "trail_id",
        "name",
        "source",
        "source_ref",
        "length_m",
        "surface",
        "trail_type",
        "mountain_name",
        "admin_region",
        "is_official",
        "raw_tags",
        "geometry",
    ]
    return gdf[final_columns]


def export_geojson(gdf: gpd.GeoDataFrame, output_path: Path):
    """
    raw_tags 객체가 GeoJSON 속성으로 잘 들어가도록 json 문자열이 아닌 dict 그대로 유지
    """
    features = json.loads(gdf.to_json())["features"]

    # 문서 스키마에 없는 feature-level id 제거
    for feature in features:
        feature.pop("id", None)

    feature_collection = {
        "type": "FeatureCollection",
        "features": features,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(feature_collection, f, ensure_ascii=False)


def main():
    default_input = get_default_input_path()
    default_output = get_default_output_path()

    parser = argparse.ArgumentParser(
        description="OSM PBF에서 등산로 way를 추출해 standard_trail.geojson 생성"
    )
    parser.add_argument(
        "--input",
        default=str(default_input),
        help=f"입력 PBF 파일 경로 (기본값: {default_input})",
    )
    parser.add_argument(
        "--output",
        default=str(default_output),
        help=f"출력 GeoJSON 파일 경로 (기본값: {default_output})",
    )
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()

    if not input_path.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {input_path}")

    print("[1/5] PBF 파일 읽기 시작")
    print(f"입력 파일: {input_path}")
    print(f"출력 파일: {output_path}")

    handler = HikingTrailWayHandler()

    # locations=True 가 중요:
    # way 안의 노드 좌표를 사용할 수 있게 해줌
    handler.apply_file(str(input_path), locations=True)

    print(f"[2/5] 추출 완료 - 원본 후보 수: {len(handler.rows)}")

    rows = deduplicate_by_source_ref(handler.rows)
    print(f"[3/5] 중복 제거 완료 - 중복 제거 후 수: {len(rows)}")

    rows = assign_trail_ids(rows)
    print("[4/5] trail_id 부여 완료")

    gdf = build_geodataframe(rows)
    print(f"[5/5] GeoDataFrame 생성 완료 - 최종 feature 수: {len(gdf)}")

    export_geojson(gdf, output_path)
    print(f"완료: {output_path}")


if __name__ == "__main__":
    main()