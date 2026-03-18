from __future__ import annotations

from config import FINAL_TRAIL_DATASET_PATH, NODE_ELEVATION_PATH
from pipeline import load_c_stage_inputs, build_final_trail_dataset, save_geojson


def main() -> None:
    print("[C단계] 데이터 로딩 시작...")
    loaded = load_c_stage_inputs()

    edge_features = loaded["edge_features"]
    node_elev_index = loaded["node_elev_index"]
    summit_list = loaded["summit_list"]
    public_surface_map = loaded["public_surface_map"]
    node_ids = loaded["node_ids"]
    duplicate_edge_ids = loaded["duplicate_edge_ids"]

    print(f"  edges: {len(edge_features)}개")
    print(f"  nodes (with elevation): {len(node_elev_index)}개")
    print(f"  summits: {len(summit_list)}개")
    print(f"  public surface map: {len(public_surface_map)}개")

    print(f"\n  node_with_elevation 저장됨: {NODE_ELEVATION_PATH}")

    print("\n[C단계] final_trail_dataset 생성 중...")
    final_dataset = build_final_trail_dataset(
        edge_features=edge_features,
        node_elev_index=node_elev_index,
        summit_list=summit_list,
        public_surface_map=public_surface_map,
        node_ids=node_ids,
        duplicate_edge_ids=duplicate_edge_ids,
    )

    save_geojson(FINAL_TRAIL_DATASET_PATH, final_dataset)

    print("\n[FINAL_TRAIL_DATASET_BUILD_RESULT]")
    print(f"  output_path: {FINAL_TRAIL_DATASET_PATH}")
    print(f"  feature_count: {len(final_dataset['features'])}")
    print(f"  duplicate_edge_ids: {sorted(duplicate_edge_ids)}")

    print("\n[feature 요약]")
    for feat in final_dataset["features"]:
        props = feat["properties"]
        print(
            f"  {props['edge_id']}"
            f" | elev: {props['elevation_start_m']} → {props['elevation_end_m']}"
            f" | diff: {props['elevation_diff_m']}"
            f" | slope: {props['slope_percent']}%"
            f" | difficulty: {props['difficulty']}"
            f" | surface: {props.get('surface')}"
            f" | summit: {props['nearest_summit_id']}"
            f" | qa: {props['qa_status']}"
        )


if __name__ == "__main__":
    main()