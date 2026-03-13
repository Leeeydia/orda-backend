from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[3]

DEFAULT_TRAIL_RAW = PROJECT_ROOT / "python" / "data" / "raw" / "gyeryong" / "gyeryong_osm_raw.geojson"
DEFAULT_OUTPUT = PROJECT_ROOT / "python" / "data" / "interim" / "a_output" / "standard_trail.geojson"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="OSM 등산로 raw GeoJSON을 A단계 표준 산출물 standard_trail.geojson으로 변환한다."
    )
    parser.add_argument(
        "--trail-raw",
        type=Path,
        default=DEFAULT_TRAIL_RAW,
        help="입력 등산로 raw GeoJSON 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="출력 standard_trail.geojson 경로",
    )
    return parser.parse_args()


def load_geojson(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if data.get("type") != "FeatureCollection":
        raise ValueError("입력 GeoJSON 최상위 type이 FeatureCollection이 아닙니다.")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError("입력 GeoJSON의 features가 리스트가 아닙니다.")

    return data


def write_geojson(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def normalize_nullable_string(value: Any) -> str | None:
    if value is None:
        return None

    text = str(value).strip()
    return text if text else None


def extract_source_ref(properties: dict[str, Any]) -> str | None:
    raw_value = properties.get("@id") or properties.get("id")
    return normalize_nullable_string(raw_value)


def is_valid_linestring_coordinates(coords: Any) -> bool:
    if not isinstance(coords, list) or len(coords) < 2:
        return False

    for point in coords:
        if not isinstance(point, list) or len(point) < 2:
            return False

        lon = point[0]
        lat = point[1]

        if not isinstance(lon, (int, float)) or not isinstance(lat, (int, float)):
            return False

        if not (-180 <= lon <= 180):
            return False
        if not (-90 <= lat <= 90):
            return False

    return True


def haversine_m(lon1: float, lat1: float, lon2: float, lat2: float) -> float:
    radius_m = 6_371_000

    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lon2 - lon1)

    a = (
            math.sin(d_phi / 2) ** 2
            + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return radius_m * c


def calculate_linestring_length_m(coords: list[list[float]]) -> float:
    total = 0.0

    for i in range(len(coords) - 1):
        lon1, lat1 = coords[i][0], coords[i][1]
        lon2, lat2 = coords[i + 1][0], coords[i + 1][1]
        total += haversine_m(lon1, lat1, lon2, lat2)

    return round(total, 2)


def build_standard_feature(raw_feature: dict[str, Any], trail_index: int) -> dict[str, Any] | None:
    geometry = raw_feature.get("geometry")
    properties = raw_feature.get("properties") or {}

    if not isinstance(geometry, dict):
        return None

    if geometry.get("type") != "LineString":
        return None

    coords = geometry.get("coordinates")
    if not is_valid_linestring_coordinates(coords):
        return None

    length_m = calculate_linestring_length_m(coords)
    if length_m <= 0:
        return None

    standard_properties = {
        "trail_id": f"T{trail_index:04d}",
        "name": normalize_nullable_string(properties.get("name")),
        "source": "OSM",
        "source_ref": extract_source_ref(properties),
        "length_m": length_m,
        "surface": normalize_nullable_string(properties.get("surface")),
        "trail_type": "trail",
        "mountain_name": None,
        "admin_region": None,
        "is_official": None,
        "raw_tags": properties if properties else None,
    }

    return {
        "type": "Feature",
        "properties": standard_properties,
        "geometry": {
            "type": "LineString",
            "coordinates": coords,
        },
    }


def convert_to_standard_trail(data: dict[str, Any]) -> tuple[dict[str, Any], int, int]:
    input_features = data.get("features", [])

    output_features: list[dict[str, Any]] = []
    excluded_count = 0

    for raw_feature in input_features:
        trail_index = len(output_features) + 1
        standard_feature = build_standard_feature(raw_feature, trail_index)

        if standard_feature is None:
            excluded_count += 1
            continue

        output_features.append(standard_feature)

    output_data = {
        "type": "FeatureCollection",
        "features": output_features,
    }

    return output_data, len(input_features), excluded_count


def main() -> None:
    args = parse_args()

    input_path = args.trail_raw
    output_path = args.output

    data = load_geojson(input_path)
    standard_data, input_count, excluded_count = convert_to_standard_trail(data)
    output_count = len(standard_data["features"])

    write_geojson(output_path, standard_data)

    print(f"입력 feature 수: {input_count}")
    print(f"출력 feature 수: {output_count}")
    print(f"제외 feature 수: {excluded_count}")
    print(f"생성 파일: {output_path}")


if __name__ == "__main__":
    main()