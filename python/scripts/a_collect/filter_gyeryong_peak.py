from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from shapely.geometry import Point, shape
from shapely.ops import unary_union


BASE_DIR = Path(__file__).resolve().parents[2]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="경계 GeoJSON 안에 포함되는 peak Point만 필터링한다."
    )
    parser.add_argument(
        "--boundary",
        type=Path,
        default=Path("data/raw/gyeryong/gyeryong_boundary.geojson"),
        help="경계 GeoJSON 경로",
    )
    parser.add_argument(
        "--peak-raw",
        type=Path,
        default=Path("data/raw/gyeryong/gyeryong_osm_peak_raw.geojson"),
        help="원본 peak GeoJSON 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/interim/a_output/gyeryong_osm_peak_filtered.geojson"),
        help="필터 결과 저장 경로",
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


def build_boundary_union(boundary_geojson: dict[str, Any]):
    polygons = []

    for feature in boundary_geojson["features"]:
        geometry = feature.get("geometry")
        if geometry is None:
            continue

        geom = shape(geometry)
        if geom.is_empty:
            continue

        polygons.append(geom)

    if not polygons:
        raise ValueError("boundary 파일에서 유효한 geometry를 찾지 못했습니다.")

    return unary_union(polygons)


def is_point_feature(feature: dict[str, Any]) -> bool:
    geometry = feature.get("geometry")
    if geometry is None:
        return False
    return geometry.get("type") == "Point"


def point_from_feature(feature: dict[str, Any]) -> Point:
    geometry = feature.get("geometry")
    coordinates = geometry.get("coordinates")

    if not isinstance(coordinates, list) or len(coordinates) < 2:
        raise ValueError("Point coordinates 형식이 잘못되었습니다.")

    lng, lat = coordinates[0], coordinates[1]

    if not isinstance(lng, (int, float)) or not isinstance(lat, (int, float)):
        raise ValueError("Point coordinates 값이 숫자가 아닙니다.")

    return Point(lng, lat)


def filter_points_within_boundary(
        peak_geojson: dict[str, Any],
        boundary_union,
) -> list[dict[str, Any]]:
    filtered_features: list[dict[str, Any]] = []

    for feature in peak_geojson["features"]:
        if not is_point_feature(feature):
            continue

        point = point_from_feature(feature)

        if boundary_union.covers(point):
            filtered_features.append(feature)

    return filtered_features


def save_geojson(path: Path, features: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    output_data = {
        "type": "FeatureCollection",
        "features": features,
    }

    with path.open("w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)


def main() -> None:
    args = parse_args()

    boundary_path = resolve_path(args.boundary)
    peak_raw_path = resolve_path(args.peak_raw)
    output_path = resolve_path(args.output)

    boundary_geojson = load_geojson(boundary_path)
    peak_geojson = load_geojson(peak_raw_path)

    boundary_union = build_boundary_union(boundary_geojson)
    filtered_features = filter_points_within_boundary(peak_geojson, boundary_union)

    save_geojson(output_path, filtered_features)

    print(f"boundary 파일: {boundary_path}")
    print(f"peak raw 파일: {peak_raw_path}")
    print(f"출력 파일: {output_path}")
    print(f"원본 peak 개수: {len(peak_geojson['features'])}")
    print(f"경계 내부 peak 개수: {len(filtered_features)}")


if __name__ == "__main__":
    main()