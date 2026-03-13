from __future__ import annotations

from typing import Any

from config import (
    NODE_ELEVATION_PATH,
    FINAL_TRAIL_DATASET_PATH,
    ABNORMAL_SLOPE_THRESHOLD,
)
from pipeline import read_geojson_features


def validate_nodes(features: list[dict[str, Any]]) -> None:
    print("[node 검증]")

    total = len(features)
    print(f"  총 node 수: {total}")

    # elevation_status 분포
    status_counts: dict[str, int] = {}
    elev_none_count = 0
    elev_values: list[float] = []
    negative_count = 0

    for feat in features:
        props = feat.get("properties", {})
        status = props.get("elevation_status", "unknown")
        status_counts[status] = status_counts.get(status, 0) + 1

        elev = props.get("elevation_m")
        if elev is None:
            elev_none_count += 1
        else:
            elev_values.append(elev)
            if elev < 0:
                negative_count += 1

    print(f"  elevation_status 분포:")
    for s, c in sorted(status_counts.items()):
        print(f"    - {s}: {c}개")

    print(f"  elevation_m이 None인 node: {elev_none_count}개")

    if elev_values:
        print(f"  고도 범위: {min(elev_values):.1f}m ~ {max(elev_values):.1f}m")
    else:
        print(f"  고도 값이 하나도 없음")

    if negative_count > 0:
        print(f"  ⚠️ 음수 고도 node: {negative_count}개 — 해안가/DEM 오류 확인 필요")

    # qa_status 분포
    qa_counts: dict[str, int] = {}
    for feat in features:
        qa = feat.get("properties", {}).get("qa_status", "unknown")
        qa_counts[qa] = qa_counts.get(qa, 0) + 1

    print(f"  qa_status 분포:")
    for q, c in sorted(qa_counts.items()):
        print(f"    - {q}: {c}개")


def validate_edges(
        features: list[dict[str, Any]],
        node_ids: set[str],
) -> None:
    print("\n[edge 검증]")

    total = len(features)
    print(f"  총 edge 수: {total}")

    ref_fail = 0
    elev_start_none = 0
    elev_end_none = 0
    diff_none = 0
    slope_none = 0
    distance_invalid = 0
    summit_none = 0
    abnormal_slope = 0

    qa_counts: dict[str, int] = {}
    difficulty_counts: dict[str, int] = {}
    slope_values: list[float] = []

    for feat in features:
        props = feat.get("properties", {})

        # node 참조 실패
        sid = props.get("start_node_id")
        eid = props.get("end_node_id")
        if sid not in node_ids or eid not in node_ids:
            ref_fail += 1

        # null 체크
        if props.get("elevation_start_m") is None:
            elev_start_none += 1
        if props.get("elevation_end_m") is None:
            elev_end_none += 1
        if props.get("elevation_diff_m") is None:
            diff_none += 1

        slope = props.get("slope_percent")
        if slope is None:
            slope_none += 1
        else:
            slope_values.append(slope)
            if abs(slope) > ABNORMAL_SLOPE_THRESHOLD:
                abnormal_slope += 1

        dist = props.get("distance_m")
        if not isinstance(dist, (int, float)) or dist <= 0:
            distance_invalid += 1

        if props.get("nearest_summit_id") is None:
            summit_none += 1

        # 분포 집계
        qa = props.get("qa_status", "unknown")
        qa_counts[qa] = qa_counts.get(qa, 0) + 1

        diff = props.get("difficulty")
        if diff:
            difficulty_counts[diff] = difficulty_counts.get(diff, 0) + 1

    print(f"  node 참조 실패: {ref_fail}개")
    print(f"  elevation_start_m null: {elev_start_none}개")
    print(f"  elevation_end_m null: {elev_end_none}개")
    print(f"  elevation_diff_m null: {diff_none}개")
    print(f"  slope_percent null: {slope_none}개")
    print(f"  distance_m 이상(<=0): {distance_invalid}개")
    print(f"  nearest_summit_id null: {summit_none}개")

    if abnormal_slope > 0:
        print(f"  ⚠️ abs(slope) > {ABNORMAL_SLOPE_THRESHOLD}%: {abnormal_slope}개")

    if slope_values:
        print(f"  slope 범위: {min(slope_values):.1f}% ~ {max(slope_values):.1f}%")

    print(f"  qa_status 분포:")
    for q, c in sorted(qa_counts.items()):
        print(f"    - {q}: {c}개")

    print(f"  difficulty 분포:")
    for d, c in sorted(difficulty_counts.items()):
        print(f"    - {d}: {c}개")


def print_sample(
        label: str,
        features: list[dict[str, Any]],
        n: int = 3,
) -> None:
    print(f"\n[{label} 샘플 (상위 {n}개)]")
    for feat in features[:n]:
        props = feat.get("properties", {})
        coords = feat.get("geometry", {}).get("coordinates")

        if isinstance(coords, list) and len(coords) >= 2:
            # Point인 경우
            if isinstance(coords[0], (int, float)):
                coord_str = f"({coords[1]:.5f}, {coords[0]:.5f})"
            else:
                # LineString인 경우 첫 좌표만
                coord_str = f"({coords[0][1]:.5f}, {coords[0][0]:.5f}) → ..."
        else:
            coord_str = "좌표 없음"

        parts = [f"{k}={v}" for k, v in props.items()]
        print(f"  {coord_str} | {', '.join(parts)}")


def main() -> None:
    print("=" * 60)
    print("C단계 산출물 검증")
    print("=" * 60)

    # node 검증
    node_features = read_geojson_features(NODE_ELEVATION_PATH)
    validate_nodes(node_features)

    # node_id set 수집 (edge 검증용)
    node_ids = set()
    for feat in node_features:
        nid = feat.get("properties", {}).get("node_id")
        if nid:
            node_ids.add(nid)

    print_sample("node", node_features)

    # edge 검증
    edge_features = read_geojson_features(FINAL_TRAIL_DATASET_PATH)
    validate_edges(edge_features, node_ids)
    print_sample("edge", edge_features)

    print("\n" + "=" * 60)
    print("검증 완료")
    print("=" * 60)


if __name__ == "__main__":
    main()