import json
from collections import OrderedDict
from pathlib import Path

import geopandas as gpd
import osmium
from pyproj import Geod
from shapely.geometry import LineString, Point

BASE_DIR = Path(__file__).resolve().parents[2]

INPUT_PBF_PATH = BASE_DIR / "data" / "raw" / "south-korea-latest.osm.pbf"
OUTPUT_TRAIL_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_osm_trail.geojson"
OUTPUT_SUMMIT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_summit.geojson"

# ──────────────────────────────────────────────
# Trail 관련 상수 (기존 그대로)
# ──────────────────────────────────────────────

EXCLUDED_HIGHWAY_VALUES = {"service", "track", "cycleway", "unclassified"}
EXCLUDED_FOOTWAY_VALUES = {"sidewalk", "crossing"}

HIKING_SURFACES = {
    "ground", "dirt", "earth", "mud", "gravel", "fine_gravel",
    "rock", "pebblestone", "sand", "woodchips", "unpaved",
}

URBAN_PAVED_SURFACES = {
    "paved", "asphalt", "concrete", "paving_stones",
    "concrete:plates", "cobblestone", "block_paving", "compacted",
}


# ──────────────────────────────────────────────
# 공통 유틸
# ──────────────────────────────────────────────

def to_plain_dict(tags) -> dict:
    return {tag.k: tag.v for tag in tags}


# ──────────────────────────────────────────────
# Trail 필터 함수들 (기존 그대로)
# ──────────────────────────────────────────────

def has_hiking_signal(tags_dict: dict) -> bool:
    hiking_related_keys = [
        "sac_scale", "trail_visibility", "via_ferrata_scale",
        "mtb:scale:uphill", "incline",
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
        "둘레길", "탐방로", "등산로", "정맥", "둘레",
        "trail", "ridge", "mountain", "forest", "계곡길",
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
    if route == "hiking" and highway not in {"path", "footway", "steps"}:
        return True

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

    if name:
        urban_name_keywords = ["로", "길", "거리", "대로", "street", "road"]
        if any(keyword in name.lower() for keyword in urban_name_keywords):
            if not looks_like_mountain_trail_name(name) and not has_hiking_signal(tags_dict):
                return True

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

    if sac_scale is not None:
        return True
    if route == "hiking" and highway in {"path", "footway", "steps"}:
        return True
    if looks_like_mountain_trail_name(name) and highway in {"path", "footway", "steps"}:
        return True
    if highway == "path":
        if surface in URBAN_PAVED_SURFACES and not has_hiking_signal(tags_dict):
            return False
        return True
    if highway == "footway":
        return has_hiking_signal(tags_dict)
    if highway == "steps":
        return has_hiking_signal(tags_dict) or looks_like_mountain_trail_name(name)

    return False


# ──────────────────────────────────────────────
# Summit 필터 함수
# ──────────────────────────────────────────────

def is_summit(tags_dict: dict) -> bool:
    """natural=peak 태그가 있는 OSM 요소를 정상(summit)으로 판별한다."""
    return tags_dict.get("natural") == "peak"


def parse_elevation(tags_dict: dict) -> float | None:
    """ele 태그에서 고도(m)를 파싱한다. 파싱 실패 시 None."""
    ele_raw = tags_dict.get("ele")
    if ele_raw is None:
        return None

    # "978 m", "1,950m", "1950" 등 다양한 포맷 대응
    cleaned = ele_raw.strip().lower()
    cleaned = cleaned.replace(",", "")
    cleaned = cleaned.replace("m", "").strip()
    # "ft" 단위가 간혹 있을 수 있으나 한국에서는 극히 드묾
    # 필요시 확장

    try:
        value = float(cleaned)
    except ValueError:
        return None

    # 비정상 값 필터링
    if value < 0 or value > 10000:
        return None

    return round(value, 1)


# ──────────────────────────────────────────────
# 통합 핸들러: Trail(way) + Summit(peak)을 동시에 수집
# ──────────────────────────────────────────────

class TrailAndSummitHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.trail_rows = []
        self.summit_rows = []
        self.geod = Geod(ellps="WGS84")

    # ── Trail (way) ──
    def way(self, w):
        tags_dict = to_plain_dict(w.tags)

        if not is_hiking_trail(tags_dict):
            return

        coords = []
        for pt in w.nodes:
            if not pt.location.valid():
                continue
            lon = pt.location.lon
            lat = pt.location.lat
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
        self.trail_rows.append(row)

    # ── Summit (peak point) ──
    # osmium의 node() 콜백을 사용하지만, 프로젝트 용어로는 summit/peak로 통일
    def node(self, n):
        if not n.location.valid():
            return

        tags_dict = to_plain_dict(n.tags)

        if not is_summit(tags_dict):
            return

        lon = n.location.lon
        lat = n.location.lat

        name = tags_dict.get("name") or tags_dict.get("name:ko")
        if not name:
            # 이름 없는 peak는 제외 (summit_id·name 필수 필드 요건)
            return

        elevation = parse_elevation(tags_dict)

        row = {
            "osm_peak_id": int(n.id),
            "name": name,
            "elevation_m": elevation,
            "source": "OSM",
            "source_ref": f"peak/{n.id}",
            "mountain_name": None,  # 별도 매칭 필요 시 C 단계에서 보강
            "admin_region": None,
            "raw_tags": tags_dict,
            "geometry": Point(lon, lat),
        }
        self.summit_rows.append(row)


# ──────────────────────────────────────────────
# Trail 후처리 (기존 그대로)
# ──────────────────────────────────────────────

def deduplicate_trails_by_source_ref(rows):
    unique = OrderedDict()
    for row in rows:
        unique[row["source_ref"]] = row
    return list(unique.values())


def assign_trail_ids(rows):
    for idx, row in enumerate(rows, start=1):
        row["trail_id"] = f"T{idx:04d}"
    return rows


def build_trail_geodataframe(rows) -> gpd.GeoDataFrame:
    gdf = gpd.GeoDataFrame(rows, geometry="geometry", crs="EPSG:4326")
    final_columns = [
        "trail_id", "name", "source", "source_ref", "length_m",
        "surface", "trail_type", "mountain_name", "admin_region",
        "is_official", "raw_tags", "geometry",
    ]
    return gdf[final_columns]


# ──────────────────────────────────────────────
# Summit 후처리
# ──────────────────────────────────────────────

def deduplicate_summits_by_source_ref(rows):
    unique = OrderedDict()
    for row in rows:
        unique[row["source_ref"]] = row
    return list(unique.values())


def assign_summit_ids(rows):
    for idx, row in enumerate(rows, start=1):
        row["summit_id"] = f"S{idx:04d}"
    return rows


def build_summit_geodataframe(rows) -> gpd.GeoDataFrame:
    gdf = gpd.GeoDataFrame(rows, geometry="geometry", crs="EPSG:4326")
    final_columns = [
        "summit_id", "name", "elevation_m", "source", "source_ref",
        "mountain_name", "admin_region", "raw_tags", "geometry",
    ]
    return gdf[final_columns]


# ──────────────────────────────────────────────
# GeoJSON 내보내기 (공통)
# ──────────────────────────────────────────────

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


# ──────────────────────────────────────────────
# main
# ──────────────────────────────────────────────

def main():
    input_path = INPUT_PBF_PATH
    trail_output = OUTPUT_TRAIL_PATH
    summit_output = OUTPUT_SUMMIT_PATH

    print("=" * 60)
    print("OSM Trail + Summit 동시 추출")
    print("=" * 60)
    print(f"입력 파일: {input_path}")
    print(f"Trail 출력: {trail_output}")
    print(f"Summit 출력: {summit_output}")
    print()

    # ── 1. PBF 한 번 읽기로 Trail + Summit 동시 수집 ──
    print("[1/7] PBF 파일 읽기 시작 (Trail + Summit 동시 수집)")
    handler = TrailAndSummitHandler()
    handler.apply_file(str(input_path), locations=True)
    print(f"  Trail 원본 후보: {len(handler.trail_rows)}개")
    print(f"  Summit 원본 후보: {len(handler.summit_rows)}개")

    # ── 2. Trail 후처리 ──
    print("[2/7] Trail 중복 제거")
    trail_rows = deduplicate_trails_by_source_ref(handler.trail_rows)
    print(f"  중복 제거 후: {len(trail_rows)}개")

    print("[3/7] Trail ID 부여")
    trail_rows = assign_trail_ids(trail_rows)

    print("[4/7] Trail GeoDataFrame 생성 및 저장")
    trail_gdf = build_trail_geodataframe(trail_rows)
    export_geojson(trail_gdf, trail_output)
    print(f"  -> {trail_output} ({len(trail_gdf)} features)")

    # ── 3. Summit 후처리 ──
    print("[5/7] Summit 중복 제거")
    summit_rows = deduplicate_summits_by_source_ref(handler.summit_rows)
    print(f"  중복 제거 후: {len(summit_rows)}개")

    print("[6/7] Summit ID 부여")
    summit_rows = assign_summit_ids(summit_rows)

    print("[7/7] Summit GeoDataFrame 생성 및 저장")
    summit_gdf = build_summit_geodataframe(summit_rows)
    export_geojson(summit_gdf, summit_output)
    print(f"  -> {summit_output} ({len(summit_gdf)} features)")

    # ── 완료 ──
    print()
    print("=" * 60)
    print("완료!")
    print(f"  Trail: {len(trail_gdf)} features -> {trail_output}")
    print(f"  Summit: {len(summit_gdf)} features -> {summit_output}")
    print("=" * 60)


if __name__ == "__main__":
    main()