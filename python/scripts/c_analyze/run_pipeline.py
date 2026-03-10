from __future__ import annotations

from pathlib import Path

from pipeline import load_c_stage_inputs, build_final_trail_dataset, save_geojson


OUTPUT_FINAL_TRAIL_DATASET_PATH = (
    Path(__file__).resolve().parents[2]
    / "output"
    / "c_stage"
    / "final_trail_dataset.geojson"
)


def main() -> None:
    loaded = load_c_stage_inputs()

    edge_features = loaded["edge_features"]
    elevation_map = loaded["elevation_map"]
    duplicate_edge_ids = loaded["duplicate_edge_ids"]

    final_dataset = build_final_trail_dataset(
        edge_features=edge_features,
        elevation_map=elevation_map,
    )

    save_geojson(OUTPUT_FINAL_TRAIL_DATASET_PATH, final_dataset)

    print("[FINAL_TRAIL_DATASET_BUILD_RESULT]")
    print(f"output_path: {OUTPUT_FINAL_TRAIL_DATASET_PATH}")
    print(f"feature_count: {len(final_dataset['features'])}")
    print(f"duplicate_edge_ids: {sorted(duplicate_edge_ids)}")


if __name__ == "__main__":
    main()