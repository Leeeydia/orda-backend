import json
from pathlib import Path

from build_edge_metrics import build_edge_metrics


def main() -> None:
    mock_dir = Path("../../data/mock/c_stage")

    with open(mock_dir / "mock_trail_network_edges.geojson", "r", encoding="utf-8") as f:
        edges_data = json.load(f)

    with open(mock_dir / "mock_node_elevation_map.json", "r", encoding="utf-8") as f:
        elevation_map = json.load(f)

    edge_features = edges_data.get("features", [])
    results = []

    for edge_feature in edge_features:
        result = build_edge_metrics(edge_feature, elevation_map)
        results.append(result)

    print("[BUILD_EDGE_METRICS_RESULT]")
    print(f"total_edges: {len(results)}")

    for result in results:
        print(result)


if __name__ == "__main__":
    main()