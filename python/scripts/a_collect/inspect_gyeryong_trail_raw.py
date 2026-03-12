from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


BASE_DIR = Path(__file__).resolve().parents[2]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="trail raw GeoJSON의 기본 속성 현황을 점검한다."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("data/raw/gyeryong/gyeryong_osm_raw.geojson"),
        help="점검할 trail raw GeoJSON 경로",
    )
    return parser.parse_args()


def resolve_path(path_value: Path) -> Path:
    if path_value.is_absolute():
        return path_value
    return BASE_DIR / path_value


def load_geojson(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise ValueError(f"{path} 파일이 존재하지 않습니다.")

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if data.get("type") != "FeatureCollection":
        raise ValueError(f"{path} 파일은 FeatureCollection 이어야 합니다.")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError(f"{path} 파일의 features 형식이 잘못되었습니다.")

    return data


def main() -> None:
    args = parse_args()
    input_path = resolve_path(args.input)

    data = load_geojson(input_path)
    features = data["features"]

    name_exists = 0
    name_missing = 0
    id_exists = 0
    id_missing = 0
    at_id_exists = 0
    at_id_missing = 0
    surface_exists = 0
    surface_missing = 0
    geometry_type_counts: dict[str, int] = {}

    for feature in features:
        props = feature.get("properties", {})
        geometry = feature.get("geometry") or {}
        geometry_type = geometry.get("type", "None")

        geometry_type_counts[geometry_type] = geometry_type_counts.get(geometry_type, 0) + 1

        if props.get("name") not in (None, ""):
            name_exists += 1
        else:
            name_missing += 1

        if props.get("id") not in (None, ""):
            id_exists += 1
        else:
            id_missing += 1

        if props.get("@id") not in (None, ""):
            at_id_exists += 1
        else:
            at_id_missing += 1

        if props.get("surface") not in (None, ""):
            surface_exists += 1
        else:
            surface_missing += 1

    print(f"입력 파일: {input_path}")
    print("전체 feature 수:", len(features))
    print("geometry 타입 분포:")
    for geometry_type, count in sorted(geometry_type_counts.items()):
        print(f"  - {geometry_type}: {count}")
    print("name 있음:", name_exists)
    print("name 없음:", name_missing)
    print("id 있음:", id_exists)
    print("id 없음:", id_missing)
    print("@id 있음:", at_id_exists)
    print("@id 없음:", at_id_missing)
    print("surface 있음:", surface_exists)
    print("surface 없음:", surface_missing)


if __name__ == "__main__":
    main()