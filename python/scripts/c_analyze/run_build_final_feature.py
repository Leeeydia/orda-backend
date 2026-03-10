import json

from build_edge_metrics import build_edge_metrics
from build_final_feature import build_final_trail_feature
from load_inputs import load_c_stage_inputs


def main():
    loaded = load_c_stage_inputs()

    edge_features = loaded["edge_features"]
    elevation_map = loaded["elevation_map"]
    duplicate_edge_ids = loaded["duplicate_edge_ids"]

    first_edge = edge_features[0]

    edge_metrics = build_edge_metrics(
        first_edge,
        elevation_map,
    )

    final_feature = build_final_trail_feature(
        edge_feature=first_edge,
        edge_metrics=edge_metrics,
    )

    print("[FINAL_TRAIL_FEATURE_PREVIEW]")
    print(json.dumps(final_feature, ensure_ascii=False, indent=2))

    print("[INPUT_CHECK]")
    print(f"duplicate_edge_ids: {sorted(duplicate_edge_ids)}")


if __name__ == "__main__":
    main()