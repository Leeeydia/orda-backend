from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Optional

from config import (
    INPUT_EDGES_PATH,
    INPUT_NODES_PATH,
    INPUT_ELEVATION_MAP_PATH,
    INPUT_SUMMIT_SOURCE_PATH,
)


def read_json_file(path: Path) -> Any:
    if not path.exists():
        raise FileNotFoundError(f"파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def write_json_file(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


def read_geojson_features(path: Path) -> list[dict[str, Any]]:
    data = read_json_file(path)

    if not isinstance(data, dict):
        raise ValueError(f"GeoJSON 형식이 아닙니다: {path}")

    if data.get("type") != "FeatureCollection":
        raise ValueError(f"FeatureCollection이 아닙니다: {path}")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError(f"features가 list가 아닙니다: {path}")

    return features


def build_node_index(
    node_features: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    node_index: dict[str, dict[str, Any]] = {}

    for feature in node_features:
        properties = feature.get("properties", {})
        node_id = properties.get("node_id")

        if not node_id:
            continue

        node_index[node_id] = feature

    return node_index


def build_summit_index(
    summit_features: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    summit_index: dict[str, dict[str, Any]] = {}

    for feature in summit_features:
        properties = feature.get("properties", {})
        summit_id = properties.get("summit_id")

        if not summit_id:
            continue

        summit_index[summit_id] = feature

    return summit_index


def collect_edge_ids(
    edge_features: list[dict[str, Any]],
) -> tuple[list[str], set[str]]:
    edge_ids: list[str] = []
    duplicate_edge_ids: set[str] = set()
    seen: set[str] = set()

    for feature in edge_features:
        properties = feature.get("properties", {})
        edge_id = properties.get("edge_id")

        if not edge_id:
            continue

        edge_ids.append(edge_id)

        if edge_id in seen:
            duplicate_edge_ids.add(edge_id)
        else:
            seen.add(edge_id)

    return edge_ids, duplicate_edge_ids


def load_c_stage_inputs() -> dict[str, Any]:
    edge_features = read_geojson_features(INPUT_EDGES_PATH)
    node_features = read_geojson_features(INPUT_NODES_PATH)
    elevation_map = read_json_file(INPUT_ELEVATION_MAP_PATH)
    summit_features = read_geojson_features(INPUT_SUMMIT_SOURCE_PATH)

    if not isinstance(elevation_map, dict):
        raise ValueError("node_elevation_map.json 형식이 dict가 아닙니다.")

    node_index = build_node_index(node_features)
    summit_index = build_summit_index(summit_features)
    edge_ids, duplicate_edge_ids = collect_edge_ids(edge_features)

    return {
        "edge_features": edge_features,
        "node_features": node_features,
        "elevation_map": elevation_map,
        "summit_features": summit_features,
        "node_index": node_index,
        "summit_index": summit_index,
        "edge_ids": edge_ids,
        "duplicate_edge_ids": duplicate_edge_ids,
    }


def calculate_elevation_gain(
    elevation_start_m: float,
    elevation_end_m: float,
) -> float:
    return round(elevation_end_m - elevation_start_m, 1)


def calculate_slope_percent(
    elevation_gain_m: float,
    distance_m: float,
) -> float:
    if distance_m <= 0:
        raise ValueError("distance_m은 0보다 커야 합니다.")

    slope_percent = (elevation_gain_m / distance_m) * 100
    return round(slope_percent, 1)


def classify_difficulty(slope_percent: float) -> str:
    abs_slope = abs(slope_percent)

    if 0 <= abs_slope < 5:
        return "easy"

    if 5 <= abs_slope < 12:
        return "medium"

    return "hard"


def build_edge_metrics(
    edge_feature: dict[str, Any],
    elevation_map: dict[str, float],
) -> dict[str, Any]:
    properties = edge_feature.get("properties", {})

    edge_id = properties.get("edge_id")
    start_node_id = properties.get("start_node_id")
    end_node_id = properties.get("end_node_id")
    distance_m = properties.get("distance_m")

    elevation_start_m: Optional[float] = elevation_map.get(start_node_id)
    elevation_end_m: Optional[float] = elevation_map.get(end_node_id)

    elevation_gain_m: Optional[float] = None
    slope_percent: Optional[float] = None
    difficulty: Optional[str] = None

    if (
        elevation_start_m is not None
        and elevation_end_m is not None
        and isinstance(distance_m, (int, float))
    ):
        elevation_gain_m = calculate_elevation_gain(
            elevation_start_m,
            elevation_end_m,
        )
        slope_percent = calculate_slope_percent(
            elevation_gain_m,
            float(distance_m),
        )
        difficulty = classify_difficulty(slope_percent)

    return {
        "edge_id": edge_id,
        "start_node_id": start_node_id,
        "end_node_id": end_node_id,
        "distance_m": distance_m,
        "elevation_start_m": elevation_start_m,
        "elevation_end_m": elevation_end_m,
        "elevation_gain_m": elevation_gain_m,
        "slope_percent": slope_percent,
        "difficulty": difficulty,
    }


def build_final_trail_feature(
    edge_feature: dict[str, Any],
    edge_metrics: dict[str, Any],
) -> dict[str, Any]:
    properties = dict(edge_feature.get("properties", {}))
    geometry = edge_feature.get("geometry")

    properties["distance_m"] = edge_metrics["distance_m"]
    properties["elevation_start_m"] = edge_metrics["elevation_start_m"]
    properties["elevation_end_m"] = edge_metrics["elevation_end_m"]
    properties["elevation_gain_m"] = edge_metrics["elevation_gain_m"]
    properties["slope_percent"] = edge_metrics["slope_percent"]
    properties["difficulty"] = edge_metrics["difficulty"]

    properties["nearest_summit_id"] = None
    properties["qa_status"] = "pass"

    return {
        "type": "Feature",
        "properties": properties,
        "geometry": geometry,
    }


def build_final_trail_dataset(
    edge_features: list[dict[str, Any]],
    elevation_map: dict[str, float],
) -> dict[str, Any]:
    final_features: list[dict[str, Any]] = []

    for edge_feature in edge_features:
        edge_metrics = build_edge_metrics(edge_feature, elevation_map)
        final_feature = build_final_trail_feature(edge_feature, edge_metrics)
        final_features.append(final_feature)

    return {
        "type": "FeatureCollection",
        "features": final_features,
    }


def save_geojson(path: Path, data: dict[str, Any]) -> None:
    if not isinstance(data, dict):
        raise ValueError("저장할 데이터가 dict가 아닙니다.")

    if data.get("type") != "FeatureCollection":
        raise ValueError("저장할 데이터가 FeatureCollection이 아닙니다.")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError("저장할 데이터의 features가 list가 아닙니다.")

    write_json_file(path, data)