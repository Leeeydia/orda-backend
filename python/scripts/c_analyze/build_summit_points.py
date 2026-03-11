from __future__ import annotations

from typing import Any

from config import INPUT_SUMMIT_PATH, SUMMIT_POINTS_PATH
from pipeline import read_geojson_features, save_geojson


def normalize_summit_feature(index: int, feature: dict[str, Any]) -> dict[str, Any]:
    properties = feature.get("properties", {})
    geometry = feature.get("geometry", {})

    if not isinstance(properties, dict):
        raise ValueError("summit feature의 properties가 dict가 아닙니다.")

    if not isinstance(geometry, dict):
        raise ValueError("summit feature의 geometry가 dict가 아닙니다.")

    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates", [])

    if geometry_type != "Point":
        raise ValueError(f"summit geometry는 Point여야 합니다. 현재값: {geometry_type}")

    if not isinstance(coordinates, list) or len(coordinates) < 2:
        raise ValueError("summit coordinates 형식이 올바르지 않습니다.")

    lng = coordinates[0]
    lat = coordinates[1]

    if not isinstance(lng, (int, float)) or not isinstance(lat, (int, float)):
        raise ValueError("summit coordinates 값이 숫자가 아닙니다.")

    summit_id = properties.get("summit_id")
    if not isinstance(summit_id, str) or not summit_id.strip():
        summit_id = f"S{index:04d}"

    name = properties.get("name")
    if not isinstance(name, str):
        name = None

    elevation_m = properties.get("elevation_m")
    if not isinstance(elevation_m, (int, float)):
        elevation_m = None
    else:
        elevation_m = float(elevation_m)

    source = properties.get("source")
    if not isinstance(source, str) or not source.strip():
        source = "unknown"

    radius_m = properties.get("radius_m")
    if not isinstance(radius_m, (int, float)):
        radius_m = None

    return {
        "type": "Feature",
        "properties": {
            "summit_id": summit_id,
            "name": name,
            "elevation_m": elevation_m,
            "source": source,
            "radius_m": radius_m,
        },
        "geometry": {
            "type": "Point",
            "coordinates": [float(lng), float(lat)],
        },
    }


def check_duplicate_summit_ids(features: list[dict[str, Any]]) -> list[str]:
    seen: set[str] = set()
    duplicates: list[str] = []

    for feature in features:
        properties = feature.get("properties", {})
        summit_id = properties.get("summit_id")

        if not isinstance(summit_id, str):
            continue

        if summit_id in seen:
            duplicates.append(summit_id)
        else:
            seen.add(summit_id)

    return duplicates


def build_summit_points_dataset(
        summit_features: list[dict[str, Any]],
) -> dict[str, Any]:
    normalized_features: list[dict[str, Any]] = []

    for index, feature in enumerate(summit_features, start=1):
        normalized_feature = normalize_summit_feature(index=index, feature=feature)
        normalized_features.append(normalized_feature)

    duplicate_ids = check_duplicate_summit_ids(normalized_features)
    if duplicate_ids:
        raise ValueError(f"중복 summit_id가 존재합니다: {duplicate_ids}")

    return {
        "type": "FeatureCollection",
        "features": normalized_features,
    }


def main() -> None:
    summit_features = read_geojson_features(INPUT_SUMMIT_PATH)
    summit_points_dataset = build_summit_points_dataset(summit_features)
    save_geojson(SUMMIT_POINTS_PATH, summit_points_dataset)

    print("[SUMMIT_POINTS_BUILD_RESULT]")
    print(f"  input_path: {INPUT_SUMMIT_PATH}")
    print(f"  output_path: {SUMMIT_POINTS_PATH}")
    print(f"  summit_count: {len(summit_points_dataset['features'])}")

    print("\n[feature 요약]")
    for feature in summit_points_dataset["features"]:
        props = feature["properties"]
        coords = feature["geometry"]["coordinates"]
        print(
            f"  {props['summit_id']}"
            f" | name: {props['name']}"
            f" | elevation: {props['elevation_m']}"
            f" | source: {props['source']}"
            f" | radius: {props['radius_m']}"
            f" | coords: {coords}"
        )


if __name__ == "__main__":
    main()