from copy import deepcopy


def build_final_trail_feature(edge_feature, edge_metrics):
    properties = deepcopy(edge_feature.get("properties", {}))
    geometry = deepcopy(edge_feature.get("geometry"))

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