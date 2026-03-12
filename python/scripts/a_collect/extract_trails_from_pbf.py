import argparse
import json
from collections import OrderedDict
from pathlib import Path

import geopandas as gpd
import osmium
from pyproj import Geod
from shapely.geometry import LineString


EXCLUDED_HIGHWAY_VALUES = {"service", "track", "cycleway", "unclassified"}
EXCLUDED_FOOTWAY_VALUES = {"sidewalk", "crossing"}

HIKING_SURFACES = {
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
}

URBAN_PAVED_SURFACES = {
    "paved",
    "asphalt",
    "concrete",
    "paving_stones",
    "concrete:plates",
    "cobblestone",
    "block_paving",
    "compacted",
}


def get_project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def get_default_input_path() -> Path:
    return get_project_root() / "data" / "raw" / "south-korea-latest.osm.pbf"


def get_default_output_path() -> Path:
    return get_project_root() / "data" / "interim" / "a_output" / "standard_trail.geojson"


def to_plain_dict(tags) -> dict:
    return {tag.k: tag.v for tag in tags}


def has_hiking_signal(tags_dict: dict) -> bool:
    hiking_related_keys = [
        "sac_scale",
        "trail_visibility",
        "via_ferrata_scale",
        "mtb:scale:uphill",
        "incline",
    ]

    if tags_dict.get("route") == "hiking":
        return True

    for key in hiking_related_keys:
        if tags_dict.get(key):
            return True

    surface = tags_dict.get("surface")
    if surface in HIKING_SURFACES:
        return True

    if tags_dict.get("highway") == "steps":
        return True

    return False


def looks_like_mountain_trail_name(name: str | None) -> bool:
    if not name:
        return False

    lowered = name.lower()

    mountain_keywords = [
        "둘레길",
        "탐방로",
        "등산로",
        "정맥",
        "둘레",
        "trail",
        "ridge",
        "mountain",
        "forest",
        "계곡길",
    ]

    return any(keyword in lowered for keyword in mountain_keywords)


def is_obviously_urban_walkway(tags_dict: dict) -> bool:
    highway = tags_dict.get("highway")
    footway = tags_dict.get("footway")
    surface = tags_dict.get("surface")
    name = tags_dict.get("name")
    route = tags_dict.get("route")
    bicycle = tags_dict.get("bicycle")
    foot = tags_dict.get("foot")
    segregated = tags_dict.get("segregated")
    lit = tags_dict.get("lit")
    width = tags_dict.get("width")

    if highway in EXCLUDED_HIGHWAY_VALUES:
        return True

    if highway == "footway" and footway in EXCLUDED_FOOTWAY_VALUES:
        return True

    if footway == "sidewalk":
        return True

    # route=hiking만 붙은 일반도로는 제외
    if route == "hiking" and highway not in {"path", "footway", "steps"}:
        return True

    # 도시형 포장 보행로 느낌
    if highway in {"path", "footway"} and surface in URBAN_PAVED_SURFACES:
        if bicycle in {"designated", "yes"} and foot in {"designated", "yes"}:
            if not has_hiking_signal(tags_dict) and not looks_like_mountain_trail_name(name):
                return True

        if segregated == "yes" and not has_hiking_signal(tags_dict):
            return True

        if lit == "yes" and not has_hiking_signal(tags_dict):
            return True

        if width is not None:
            try:
                if float(width) >= 2.5 and not has_hiking_signal(tags_dict):
                    return True
            except ValueError:
                pass

    # 일반 생활도로 이름 느낌
    if name:
        urban_name_keywords = ["로", "길", "거리", "대로", "street", "road"]
        if any(keyword in name.lower() for keyword in urban_name_keywords):
            if not looks_like_mountain_trail_name(name) and not has_hiking_signal(tags_dict):
                return True

    # 도시 산책로 느낌 강한 조합
    if highway == "path" and surface in URBAN_PAVED_SURFACES:
        if bicycle in {"designated", "yes"} and foot in {"designated", "yes"}:
            if not has_hiking_signal(tags_dict) and not looks_like_mountain_trail_name(name):
                return True

    return False


def is_hiking_trail(tags_dict: dict) -> bool:
    highway = tags_dict.get("highway")
    route = tags_dict.get("route")
    sac_scale = tags_dict.get("sac_scale")
    surface = tags_dict.get("surface")
    name = tags_dict.get("name")

    if highway in EXCLUDED_HIGHWAY_VALUES:
        return False

    if is_obviously_urban_walkway(tags_dict):
        return False

    # 가장 강한 힌트
    if sac_scale is not None:
        return True

    # path + hiking relation
    if route == "hiking" and highway in {"path", "footway", "steps"}:
        return True

    # 산길 이름 힌트
    if looks_like_mountain_trail_name(name) and highway in {"path", "footway", "steps"}:
        return True

    # path는 기본 포함하되 도시형 포장 path는 위에서 대부분 제거
    if highway == "path":
        # 포장 path는 산길 힌트가 없으면 제외
        if surface in URBAN_PAVED_SURFACES and not has_hiking_signal(tags_dict):
            return False
        return True

    # footway는 산길 힌트가 있을 때만 포함
    if highway == "footway":
        return has_hiking_signal(tags_dict)

    # steps는 등산 계단일 가능성이 높지만, 최소한 hiking 신호가 있거나 이름이 산길이어야 함
    if highway == "steps":
        return has_hiking_signal(tags_dict) or looks_like_mountain_trail_name(name)

    return False


class HikingTrailWayHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.geod = Geod(ellps="WGS84")

    def way(self, w):
        tags_dict = to_plain_dict(w.tags)

        if not is_hiking_trail(tags_dict):
            return

        coords = []
        for node in w.nodes:
            if not node.location.valid():
                continue

            lon = node.location.lon
            lat = node.location.lat
            coords.append((lon, lat))

        if len(coords) < 2:
            return

        try:
            line = LineString(coords)
        except Exception:
            return

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
    unique = OrderedDict()
    for row in rows:
        unique[row["source_ref"]] = row
    return list(unique.values())


def assign_trail_ids(rows):
    for idx, row in enumerate(rows, start=1):
        row["trail_id"] = f"T{idx:04d}"
    return rows


def build_geodataframe(rows) -> gpd.GeoDataFrame:
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
    features = json.loads(gdf.to_json())["features"]

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