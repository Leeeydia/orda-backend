from __future__ import annotations

from typing import Any, Optional

from metrics import (
    calculate_elevation_gain,
    calculate_slope_percent,
    classify_difficulty,
)


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