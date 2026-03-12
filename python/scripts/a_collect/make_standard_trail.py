import json
import math
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]

INPUT_PATH = BASE_DIR / "data" / "raw" / "gyeryong" / "gyeryong_osm_raw.geojson"
OUTPUT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_trail.geojson"


def load_geojson(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_geojson(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def haversine_m(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    r = 6371000
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lng2 - lng1)

    a = (
            math.sin(d_phi / 2) ** 2
            + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return r * c


def calc_linestring_length_m(coords: list) -> float | None:
    if not isinstance(coords, list) or len(coords) < 2:
        return None

    total = 0.0

    for i in range(len(coords) - 1):
        p1 = coords[i]
        p2 = coords[i + 1]

        if (
                not isinstance(p1, list)
                or not isinstance(p2, list)
                or len(p1) < 2
                or len(p2) < 2
        ):
            return None

        lng1, lat1 = p1[0], p1[1]
        lng2, lat2 = p2[0], p2[1]

        if None in (lng1, lat1, lng2, lat2):
            return None

        total += haversine_m(lng1, lat1, lng2, lat2)

    return round(total, 1)


def make_source_ref(properties: dict) -> str | None:
    at_id = properties.get("@id")
    if at_id not in (None, ""):
        return str(at_id)

    raw_id = properties.get("id")
    if raw_id not in (None, ""):
        return str(raw_id)

    return None


def make_feature(index: int, feature: dict) -> dict | None:
    geometry = feature.get("geometry")
    properties = feature.get("properties") or {}

    if not geometry:
        return None

    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates")

    if geometry_type != "LineString":
        return None

    if not isinstance(coordinates, list) or len(coordinates) < 2:
        return None

    length_m = calc_linestring_length_m(coordinates)
    source_ref = make_source_ref(properties)

    standardized_properties = {
        "trail_id": f"T{index:04d}",
        "name": properties.get("name") if properties.get("name") not in ("", None) else None,
        "source": "OSM",
        "source_ref": source_ref,
        "length_m": length_m,
        "surface": properties.get("surface") if properties.get("surface") not in ("", None) else None,
        "trail_type": "trail",
        "mountain_name": None,
        "admin_region": None,
        "is_official": None,
        "raw_tags": properties,
    }

    return {
        "type": "Feature",
        "properties": standardized_properties,
        "geometry": {
            "type": "LineString",
            "coordinates": coordinates,
        },
    }


def main() -> None:
    data = load_geojson(INPUT_PATH)
    features = data.get("features", [])

    output_features = []
    skipped_count = 0

    next_id = 1
    for feature in features:
        standardized = make_feature(next_id, feature)
        if standardized is None:
            skipped_count += 1
            continue

        output_features.append(standardized)
        next_id += 1

    output = {
        "type": "FeatureCollection",
        "features": output_features,
    }

    save_geojson(OUTPUT_PATH, output)

    print("===== standard_trail.geojson 생성 완료 =====")
    print(f"입력 feature 수: {len(features)}")
    print(f"출력 feature 수: {len(output_features)}")
    print(f"제외 feature 수: {skipped_count}")
    print(f"출력 파일: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()