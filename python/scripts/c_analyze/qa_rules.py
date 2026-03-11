from __future__ import annotations

from typing import Any


def evaluate_edge_qa(
    edge_feature: dict[str, Any],
    node_ids: set[str],
    edge_metrics: dict[str, Any],
    duplicate_edge_ids: set[str] | None = None,
) -> dict[str, Any]:
    properties = edge_feature.get("properties", {})
    geometry = edge_feature.get("geometry", {})

    edge_id = properties.get("edge_id")
    start_node_id = properties.get("start_node_id")
    end_node_id = properties.get("end_node_id")
    distance_m = properties.get("distance_m")
    geometry_type = geometry.get("type")

    slope_percent = edge_metrics.get("slope_percent")
    elevation_start_m = edge_metrics.get("elevation_start_m")
    elevation_end_m = edge_metrics.get("elevation_end_m")

    reasons: list[str] = []

    if not edge_id:
        reasons.append("missing_edge_id")

    if not start_node_id or start_node_id not in node_ids:
        reasons.append("missing_start_node")

    if not end_node_id or end_node_id not in node_ids:
        reasons.append("missing_end_node")

    if geometry_type != "LineString":
        reasons.append("invalid_geometry")

    if not isinstance(distance_m, (int, float)) or distance_m <= 0:
        reasons.append("invalid_distance")

    if elevation_start_m is None or elevation_end_m is None:
        reasons.append("dem_sample_failed")

    if slope_percent is None:
        reasons.append("slope_calc_failed")

    if isinstance(slope_percent, (int, float)) and abs(slope_percent) > 100:
        reasons.append("abnormal_slope")

    if duplicate_edge_ids and edge_id in duplicate_edge_ids:
        reasons.append("duplicate_edge_id")

    qa_status = "pass" if not reasons else "fail"

    return {
        "qa_status": qa_status,
        "qa_reasons": reasons,
    }