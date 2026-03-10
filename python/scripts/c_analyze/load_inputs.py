from __future__ import annotations

import json
from pathlib import Path
from typing import Any

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


def print_input_summary(loaded: dict[str, Any]) -> None:
    print("[C_STAGE_INPUT_SUMMARY]")
    print(f"edges: {len(loaded['edge_features'])}")
    print(f"nodes: {len(loaded['node_features'])}")
    print(f"elevation_map keys: {len(loaded['elevation_map'])}")
    print(f"summits: {len(loaded['summit_features'])}")
    print(f"duplicate_edge_ids: {sorted(loaded['duplicate_edge_ids'])}")


if __name__ == "__main__":
    loaded_data = load_c_stage_inputs()
    print_input_summary(loaded_data)