from __future__ import annotations

import json
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]

TRAIL_PATH = PROJECT_ROOT / "data" / "interim" / "a_output" / "standard_trail.geojson"
SUMMIT_PATH = PROJECT_ROOT / "data" / "interim" / "a_output" / "standard_summit.geojson"


def read_geojson(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def validate_feature_collection(data: dict, label: str) -> list[str]:
    errors: list[str] = []

    if data.get("type") != "FeatureCollection":
        errors.append(f"[{label}] type이 FeatureCollection이 아닙니다.")

    features = data.get("features")
    if not isinstance(features, list):
        errors.append(f"[{label}] features가 리스트가 아닙니다.")

    return errors


def validate_trail(data: dict) -> list[str]:
    errors: list[str] = []
    features = data.get("features", [])

    seen_ids: set[str] = set()

    for idx, feature in enumerate(features, start=1):
        props = feature.get("properties", {})
        geometry = feature.get("geometry", {})

        trail_id = props.get("trail_id")
        source = props.get("source")
        length_m = props.get("length_m")
        geometry_type = geometry.get("type")
        coordinates = geometry.get("coordinates")

        if not trail_id:
            errors.append(f"[trail] {idx}번 feature: trail_id 누락")
        elif trail_id in seen_ids:
            errors.append(f"[trail] {idx}번 feature: trail_id 중복 ({trail_id})")
        else:
            seen_ids.add(trail_id)

        if not source:
            errors.append(f"[trail] {idx}번 feature: source 누락")

        if geometry_type != "LineString":
            errors.append(f"[trail] {idx}번 feature: geometry.type이 LineString이 아님 ({geometry_type})")

        if not coordinates:
            errors.append(f"[trail] {idx}번 feature: coordinates 누락")

        if length_m is None:
            errors.append(f"[trail] {idx}번 feature: length_m 누락")
        elif not isinstance(length_m, (int, float)):
            errors.append(f"[trail] {idx}번 feature: length_m가 숫자가 아님 ({length_m})")
        elif length_m <= 0:
            errors.append(f"[trail] {idx}번 feature: length_m가 0 이하 ({length_m})")

    return errors


def validate_summit(data: dict) -> list[str]:
    errors: list[str] = []
    features = data.get("features", [])

    seen_ids: set[str] = set()

    for idx, feature in enumerate(features, start=1):
        props = feature.get("properties", {})
        geometry = feature.get("geometry", {})

        summit_id = props.get("summit_id")
        source_ref = props.get("source_ref")
        elevation_m = props.get("elevation_m")
        geometry_type = geometry.get("type")
        coordinates = geometry.get("coordinates")

        if not summit_id:
            errors.append(f"[summit] {idx}번 feature: summit_id 누락")
        elif summit_id in seen_ids:
            errors.append(f"[summit] {idx}번 feature: summit_id 중복 ({summit_id})")
        else:
            seen_ids.add(summit_id)

        if not source_ref:
            errors.append(f"[summit] {idx}번 feature: source_ref 누락")

        if geometry_type != "Point":
            errors.append(f"[summit] {idx}번 feature: geometry.type이 Point가 아님 ({geometry_type})")

        if not coordinates:
            errors.append(f"[summit] {idx}번 feature: coordinates 누락")

        if elevation_m is not None and not isinstance(elevation_m, (int, float)):
            errors.append(f"[summit] {idx}번 feature: elevation_m가 숫자/null이 아님 ({elevation_m})")

    return errors


def main() -> None:
    trail_data = read_geojson(TRAIL_PATH)
    summit_data = read_geojson(SUMMIT_PATH)

    errors: list[str] = []

    errors.extend(validate_feature_collection(trail_data, "trail"))
    errors.extend(validate_feature_collection(summit_data, "summit"))

    errors.extend(validate_trail(trail_data))
    errors.extend(validate_summit(summit_data))

    if errors:
        print("=== A단계 산출물 검수 실패 ===")
        for error in errors:
            print(error)
    else:
        print("=== A단계 산출물 검수 통과 ===")
        print(f"trail feature 수: {len(trail_data.get('features', []))}")
        print(f"summit feature 수: {len(summit_data.get('features', []))}")


if __name__ == "__main__":
    main()