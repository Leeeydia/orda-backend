from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Optional

import rasterio

from config import (
    INPUT_EDGES_PATH,
    INPUT_NODES_PATH,
    INPUT_DEM_PATH,
    INPUT_SUMMIT_PATH,
    NODE_ELEVATION_PATH,
    SUMMIT_LINK_MAX_DISTANCE_M,
)


# ──────────────────────────────────────────────
# 파일 읽기/쓰기 유틸
# ──────────────────────────────────────────────

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


# ──────────────────────────────────────────────
# DEM 관련 함수
# ──────────────────────────────────────────────

def open_dem(dem_path: Path):
    """DEM 파일을 열어서 rasterio dataset 객체를 반환한다."""
    if not dem_path.exists():
        raise FileNotFoundError(f"DEM 파일이 없습니다: {dem_path}")
    return rasterio.open(dem_path)


def sample_elevation(
        dem_dataset, lng: float, lat: float,
) -> tuple[Optional[float], str]:
    """
    DEM에서 [lng, lat] 좌표의 고도값을 추출한다.

    반환: (elevation_m, status)
    - status: "ok" | "nodata" | "out_of_bounds" | "error:{에러타입}"
    """
    try:
        # 좌표가 DEM bounds 안에 있는지 먼저 확인
        bounds = dem_dataset.bounds
        if not (bounds.left <= lng <= bounds.right
                and bounds.bottom <= lat <= bounds.top):
            return None, "out_of_bounds"

        # rasterio.sample()은 [(x, y)] 형식 = [(lng, lat)] 형식
        values = list(dem_dataset.sample([(lng, lat)]))
        if len(values) == 0:
            return None, "no_sample"

        elevation = float(values[0][0])

        # nodata 값 체크
        if dem_dataset.nodata is not None and elevation == dem_dataset.nodata:
            return None, "nodata"

        return round(elevation, 1), "ok"

    except Exception as e:
        return None, f"error:{type(e).__name__}"


# ──────────────────────────────────────────────
# node 고도 산출물 생성
# ──────────────────────────────────────────────

def build_node_elevation(
        node_features: list[dict[str, Any]],
        dem_dataset,
) -> dict[str, Any]:
    """
    모든 node에 대해 DEM 고도를 샘플링하여
    node_with_elevation FeatureCollection + lookup index를 생성한다.

    - DEM 샘플링 책임이 이 함수에 고정됨
    - edge 계산은 이 결과를 참조만 함
    - trail_nodes 테이블 적재 데이터와 직접 매핑됨

    반환: {
        "geojson": FeatureCollection (저장용),
        "index": { node_id: {"elevation_m": float|None, "status": str} }
    }
    """
    features: list[dict[str, Any]] = []
    elev_index: dict[str, dict[str, Any]] = {}

    for feature in node_features:
        props = feature.get("properties", {})
        geom = feature.get("geometry", {})
        coords = geom.get("coordinates", [])
        node_id = props.get("node_id")

        if not node_id or len(coords) < 2:
            continue

        lng, lat = coords[0], coords[1]
        elevation_m, status = sample_elevation(dem_dataset, lng, lat)

        qa_status = "pass" if status == "ok" else f"fail:{status}"

        elev_index[node_id] = {
            "elevation_m": elevation_m,
            "status": status,
        }

        new_props = {
            **props,
            "elevation_m": elevation_m,
            "elevation_status": status,
            "qa_status": qa_status,
        }

        features.append({
            "type": "Feature",
            "properties": new_props,
            "geometry": geom,
        })

    geojson = {
        "type": "FeatureCollection",
        "features": features,
    }

    # 요약
    total = len(features)
    ok = sum(1 for v in elev_index.values() if v["status"] == "ok")
    fail = total - ok
    print(f"  [node elevation] 총 {total}개 중 {ok}개 정상, {fail}개 실패")

    if fail > 0:
        from collections import Counter
        fail_reasons = Counter(
            v["status"] for v in elev_index.values() if v["status"] != "ok"
        )
        for reason, count in fail_reasons.items():
            print(f"    - {reason}: {count}개")

    return {"geojson": geojson, "index": elev_index}


# ──────────────────────────────────────────────
# 인덱스 빌더
# ──────────────────────────────────────────────

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


def build_summit_list(
        summit_features: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """
    summit feature 목록에서 summit_id와 좌표를 추출해 리스트로 반환.
    nearest_summit_id 계산에 사용.
    """
    summits = []

    for feature in summit_features:
        properties = feature.get("properties", {})
        geometry = feature.get("geometry", {})
        coords = geometry.get("coordinates", [])

        summit_id = properties.get("summit_id")
        if not summit_id or len(coords) < 2:
            continue

        summits.append({
            "summit_id": summit_id,
            "lng": coords[0],
            "lat": coords[1],
        })

    return summits


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


# ──────────────────────────────────────────────
# 거리 계산 (Haversine)
# ──────────────────────────────────────────────

def haversine_distance_m(
        lng1: float, lat1: float,
        lng2: float, lat2: float,
) -> float:
    """
    두 좌표 사이의 거리를 미터 단위로 계산한다 (Haversine 공식).
    입력은 [lng, lat] 순서 (도 단위).
    """
    R = 6371000.0

    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)

    a = (
            math.sin(dlat / 2) ** 2
            + math.cos(lat1_rad) * math.cos(lat2_rad)
            * math.sin(dlng / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return R * c


# ──────────────────────────────────────────────
# edge 중점 계산 (실제 선 길이 기준)
# ──────────────────────────────────────────────

def get_edge_midpoint(geometry: dict[str, Any]) -> Optional[tuple[float, float]]:
    """
    LineString geometry에서 실제 선 길이 기준 중점 좌표를 반환한다.
    좌표 배열의 중간 인덱스가 아니라, 누적 거리 기준으로 보간한다.
    반환: (lng, lat) 또는 None
    """
    coords = geometry.get("coordinates", [])
    if not coords or len(coords) < 2:
        return None

    if len(coords) == 2:
        return (
            (coords[0][0] + coords[1][0]) / 2,
            (coords[0][1] + coords[1][1]) / 2,
        )

    # 각 segment 길이 누적
    segment_lengths: list[float] = []
    for i in range(len(coords) - 1):
        d = haversine_distance_m(
            coords[i][0], coords[i][1],
            coords[i + 1][0], coords[i + 1][1],
        )
        segment_lengths.append(d)

    total_length = sum(segment_lengths)
    half_length = total_length / 2

    # 누적 길이가 half_length를 넘는 segment에서 보간
    cumulative = 0.0
    for i, seg_len in enumerate(segment_lengths):
        if cumulative + seg_len >= half_length:
            remaining = half_length - cumulative
            ratio = remaining / seg_len if seg_len > 0 else 0
            lng = coords[i][0] + ratio * (coords[i + 1][0] - coords[i][0])
            lat = coords[i][1] + ratio * (coords[i + 1][1] - coords[i][1])
            return (lng, lat)
        cumulative += seg_len

    # fallback (정상적으로는 여기 오지 않음)
    return (coords[-1][0], coords[-1][1])


# ──────────────────────────────────────────────
# nearest summit 계산
# ──────────────────────────────────────────────

def find_nearest_summit_id(
        midpoint_lng: float,
        midpoint_lat: float,
        summit_list: list[dict[str, Any]],
        max_distance_m: float,
) -> Optional[str]:
    """
    edge 중점에서 가장 가까운 정상을 찾되,
    max_distance_m 이내일 때만 summit_id를 반환한다.
    """
    nearest_id: Optional[str] = None
    nearest_dist: float = float("inf")

    for summit in summit_list:
        dist = haversine_distance_m(
            midpoint_lng, midpoint_lat,
            summit["lng"], summit["lat"],
        )
        if dist < nearest_dist:
            nearest_dist = dist
            nearest_id = summit["summit_id"]

    if nearest_dist <= max_distance_m:
        return nearest_id

    return None


# ──────────────────────────────────────────────
# 계산 함수들
# ──────────────────────────────────────────────

def calculate_slope_percent(
        elevation_diff_m: float,
        distance_m: float,
) -> float:
    if distance_m <= 0:
        raise ValueError("distance_m은 0보다 커야 합니다.")

    slope_percent = (elevation_diff_m / distance_m) * 100
    return round(slope_percent, 1)


def classify_difficulty(slope_percent: float) -> str:
    abs_slope = abs(slope_percent)

    if 0 <= abs_slope < 5:
        return "easy"

    if 5 <= abs_slope < 12:
        return "medium"

    return "hard"


# ──────────────────────────────────────────────
# 입력 로딩
# ──────────────────────────────────────────────

def load_c_stage_inputs() -> dict[str, Any]:
    """
    C단계에 필요한 모든 입력 데이터를 로딩한다.
    - B단계 산출물: edges, nodes
    - DEM: rasterio dataset → node 고도 샘플링 후 닫기
    - 정상 데이터: summit features

    흐름:
    1. 파일 로딩
    2. DEM 열기 → node 고도 샘플링 → DEM 닫기
    3. node_with_elevation.geojson 저장
    4. 인덱스 생성 후 반환
    """
    edge_features = read_geojson_features(INPUT_EDGES_PATH)
    node_features = read_geojson_features(INPUT_NODES_PATH)
    summit_features = read_geojson_features(INPUT_SUMMIT_PATH)

    # DEM 열기 → node 고도 샘플링 → 닫기
    dem_dataset = open_dem(INPUT_DEM_PATH)
    node_elev_result = build_node_elevation(node_features, dem_dataset)
    dem_dataset.close()

    # node_with_elevation.geojson 저장
    save_geojson(NODE_ELEVATION_PATH, node_elev_result["geojson"])

    node_index = build_node_index(node_features)
    summit_list = build_summit_list(summit_features)
    edge_ids, duplicate_edge_ids = collect_edge_ids(edge_features)
    node_ids = set(node_index.keys())

    return {
        "edge_features": edge_features,
        "node_features": node_features,
        "node_elev_index": node_elev_result["index"],
        "summit_list": summit_list,
        "edge_ids": edge_ids,
        "duplicate_edge_ids": duplicate_edge_ids,
        "node_ids": node_ids,
    }


# ──────────────────────────────────────────────
# edge 메트릭 계산 (node 고도 참조 방식)
# ──────────────────────────────────────────────

def build_edge_metrics(
        edge_feature: dict[str, Any],
        node_elev_index: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    """
    node_elev_index에서 고도를 참조하여 edge 메트릭을 계산한다.
    DEM 직접 접근 없음.

    로직:
    1. start_node_id, end_node_id로 node 고도를 lookup
    2. elevation_diff_m = end - start (부호 있는 고도 차이)
       ※ 현재는 end-start 단순 차이값이며, 진짜 누적 상승고도가 아님
    3. slope_percent = (elevation_diff_m / distance_m) * 100
    4. difficulty = easy / medium / hard (절댓값 기준)
    """
    properties = edge_feature.get("properties", {})

    edge_id = properties.get("edge_id")
    start_node_id = properties.get("start_node_id")
    end_node_id = properties.get("end_node_id")
    distance_m = properties.get("distance_m")

    # node 고도 참조
    start_elev = node_elev_index.get(start_node_id, {})
    end_elev = node_elev_index.get(end_node_id, {})

    elevation_start_m = start_elev.get("elevation_m")
    elevation_end_m = end_elev.get("elevation_m")

    # 계산
    elevation_diff_m: Optional[float] = None
    slope_percent: Optional[float] = None
    difficulty: Optional[str] = None

    if (
            elevation_start_m is not None
            and elevation_end_m is not None
            and isinstance(distance_m, (int, float))
            and distance_m > 0
    ):
        elevation_diff_m = round(elevation_end_m - elevation_start_m, 1)
        slope_percent = calculate_slope_percent(
            elevation_diff_m, float(distance_m)
        )
        difficulty = classify_difficulty(slope_percent)

    return {
        "edge_id": edge_id,
        "start_node_id": start_node_id,
        "end_node_id": end_node_id,
        "distance_m": distance_m,
        "elevation_start_m": elevation_start_m,
        "elevation_end_m": elevation_end_m,
        "elevation_diff_m": elevation_diff_m,
        "slope_percent": slope_percent,
        "difficulty": difficulty,
    }


# ──────────────────────────────────────────────
# 최종 feature 빌드
# ──────────────────────────────────────────────

def build_final_trail_feature(
        edge_feature: dict[str, Any],
        edge_metrics: dict[str, Any],
        nearest_summit_id: Optional[str],
        qa_result: dict[str, Any],
) -> dict[str, Any]:
    """
    문서 기준 final_trail_dataset.geojson의 feature 하나를 생성한다.

    필드 순서 (공식문서 기준, elevation_gain_m → elevation_diff_m 변경):
    edge_id, start_node_id, end_node_id, distance_m,
    elevation_start_m, elevation_end_m, elevation_diff_m,
    slope_percent, difficulty, nearest_summit_id, qa_status,
    geometry
    """
    geometry = edge_feature.get("geometry")

    properties = {
        "edge_id": edge_metrics["edge_id"],
        "start_node_id": edge_metrics["start_node_id"],
        "end_node_id": edge_metrics["end_node_id"],
        "distance_m": edge_metrics["distance_m"],
        "elevation_start_m": edge_metrics["elevation_start_m"],
        "elevation_end_m": edge_metrics["elevation_end_m"],
        "elevation_diff_m": edge_metrics["elevation_diff_m"],
        "slope_percent": edge_metrics["slope_percent"],
        "difficulty": edge_metrics["difficulty"],
        "nearest_summit_id": nearest_summit_id,
        "qa_status": qa_result["qa_status"],
    }

    return {
        "type": "Feature",
        "properties": properties,
        "geometry": geometry,
    }


# ──────────────────────────────────────────────
# 전체 dataset 빌드
# ──────────────────────────────────────────────

def build_final_trail_dataset(
        edge_features: list[dict[str, Any]],
        node_elev_index: dict[str, dict[str, Any]],
        summit_list: list[dict[str, Any]],
        node_ids: set[str],
        duplicate_edge_ids: set[str],
) -> dict[str, Any]:
    """
    모든 edge를 순회하며 final_trail_dataset.geojson을 생성한다.

    각 edge마다:
    1. node_elev_index에서 고도 참조 → diff/slope/difficulty 계산
    2. edge 중점 기준 nearest_summit_id 계산 (300m 이내)
    3. qa_rules로 qa_status 계산
    4. 최종 feature 생성
    """
    from qa_rules import evaluate_edge_qa

    final_features: list[dict[str, Any]] = []

    for edge_feature in edge_features:
        # 1. elevation / slope / difficulty
        edge_metrics = build_edge_metrics(
            edge_feature, node_elev_index
        )

        # 2. nearest_summit_id
        nearest_summit_id: Optional[str] = None
        geometry = edge_feature.get("geometry", {})
        midpoint = get_edge_midpoint(geometry)

        if midpoint is not None:
            nearest_summit_id = find_nearest_summit_id(
                midpoint[0], midpoint[1],
                summit_list,
                SUMMIT_LINK_MAX_DISTANCE_M,
            )

        # 3. qa_status
        qa_result = evaluate_edge_qa(
            edge_feature=edge_feature,
            node_ids=node_ids,
            edge_metrics=edge_metrics,
            duplicate_edge_ids=duplicate_edge_ids,
        )

        # 4. 최종 feature 생성
        final_feature = build_final_trail_feature(
            edge_feature, edge_metrics,
            nearest_summit_id, qa_result,
        )
        final_features.append(final_feature)

    return {
        "type": "FeatureCollection",
        "features": final_features,
    }


# ──────────────────────────────────────────────
# 저장
# ──────────────────────────────────────────────

def save_geojson(path: Path, data: dict[str, Any]) -> None:
    if not isinstance(data, dict):
        raise ValueError("저장할 데이터가 dict가 아닙니다.")

    if data.get("type") != "FeatureCollection":
        raise ValueError("저장할 데이터가 FeatureCollection이 아닙니다.")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError("저장할 데이터의 features가 list가 아닙니다.")

    write_json_file(path, data)