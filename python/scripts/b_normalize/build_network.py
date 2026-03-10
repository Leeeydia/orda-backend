from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from pyproj import Geod


# =========================
# 1. 기본 설정
# =========================

# 현재 파일 기준으로 python/ 폴더를 찾기 위한 설정
# build_network.py 위치:
# python/scripts/b_normalize/build_network.py
# 그래서 parents[2]가 python/ 폴더가 됨
PROJECT_ROOT = Path(__file__).resolve().parents[2]

# 입력 / 출력 경로
INPUT_PATH = PROJECT_ROOT / "data" / "mock" / "standard_trail.geojson"
OUTPUT_DIR = PROJECT_ROOT / "data" / "interim" / "b_output"
EDGES_OUTPUT_PATH = OUTPUT_DIR / "trail_network_edges.geojson"
NODES_OUTPUT_PATH = OUTPUT_DIR / "trail_network_nodes.geojson"

# WGS84 기준 거리 계산용 객체
GEOD = Geod(ellps="WGS84")

# 좌표 반올림 자릿수
# 같은 좌표를 node 하나로 묶기 위한 용도
COORD_PRECISION = 7


# =========================
# 2. 공통 함수
# =========================

def read_geojson(path: Path) -> dict:
    """GeoJSON 파일을 읽어서 dict로 반환"""
    if not path.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if data.get("type") != "FeatureCollection":
        raise ValueError("입력 파일의 최상위 type은 FeatureCollection이어야 합니다.")

    return data


def write_geojson(path: Path, features: list[dict]) -> None:
    """FeatureCollection 형태로 GeoJSON 저장"""
    path.parent.mkdir(parents=True, exist_ok=True)

    output = {
        "type": "FeatureCollection",
        "features": features
    }

    with path.open("w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)


def round_coord(coord: list[float], precision: int = COORD_PRECISION) -> list[float]:
    """
    좌표를 반올림해서 반환
    [lng, lat] 순서를 유지
    """
    lng = round(float(coord[0]), precision)
    lat = round(float(coord[1]), precision)
    return [lng, lat]


def make_node_key(coord: list[float]) -> tuple[float, float]:
    """
    같은 좌표인지 비교할 때 사용할 key
    dict의 key로 쓰기 위해 tuple로 변환
    """
    rounded = round_coord(coord)
    return rounded[0], rounded[1]


def make_line_key(coords: list[list[float]]) -> tuple:
    """
    완전 동일하거나 역방향으로만 뒤집힌 동일 선분을
    같은 것으로 보기 위한 key 생성
    """
    rounded_coords = tuple(make_node_key(c) for c in coords)
    reversed_coords = tuple(reversed(rounded_coords))
    return min(rounded_coords, reversed_coords)


def calculate_length_m(coords: list[list[float]]) -> float:
    """
    WGS84 좌표([lng, lat]) 기준 실제 길이(m) 계산
    """
    lons = [c[0] for c in coords]
    lats = [c[1] for c in coords]
    length = GEOD.line_length(lons, lats)
    return round(abs(length), 1)


def split_to_lines(geometry: dict | None) -> list[list[list[float]]]:
    """
    geometry를 받아서 LineString 목록으로 통일해서 반환

    반환 형태 예시:
    [
      [[lng, lat], [lng, lat], ...],   # 첫 번째 LineString
      [[lng, lat], [lng, lat], ...]    # 두 번째 LineString
    ]
    """
    if geometry is None:
        return []

    geom_type = geometry.get("type")
    coords = geometry.get("coordinates", [])

    if geom_type == "LineString":
        return [coords]

    if geom_type == "MultiLineString":
        return coords

    # B 단계 입력은 원칙적으로 LineString / MultiLineString만 받음
    return []


def get_or_create_node_id(
        coord: list[float],
        nodes_by_key: dict,
        node_features: dict,
        next_node_number: int
) -> tuple[str, int]:
    """
    좌표를 보고 기존 node가 있으면 기존 node_id 반환,
    없으면 새 node 생성
    """
    key = make_node_key(coord)

    if key in nodes_by_key:
        return nodes_by_key[key], next_node_number

    node_id = f"N{next_node_number:04d}"
    nodes_by_key[key] = node_id

    node_features[node_id] = {
        "type": "Feature",
        "properties": {
            "node_id": node_id,
            "node_type": "pending",  # 마지막에 계산해서 채움
            "degree": 0,
            "trail_refs": []
        },
        "geometry": {
            "type": "Point",
            "coordinates": list(key)  # tuple -> list
        }
    }

    return node_id, next_node_number + 1


# =========================
# 3. 메인 로직
# =========================

def build_network() -> None:
    data = read_geojson(INPUT_PATH)
    input_features = data.get("features", [])

    print(f"입력 feature 수: {len(input_features)}")

    # node 관리용
    nodes_by_key: dict[tuple[float, float], str] = {}
    node_features: dict[str, dict] = {}

    # node별 연결 정보
    node_edge_refs: defaultdict[str, set[str]] = defaultdict(set)
    node_trail_refs: defaultdict[str, set[str]] = defaultdict(set)

    # start / end 판별용 카운트
    node_start_count: defaultdict[str, int] = defaultdict(int)
    node_end_count: defaultdict[str, int] = defaultdict(int)

    # edge 목록
    edge_features: list[dict] = []

    # 중복 제거용
    seen_line_keys: set[tuple] = set()

    next_node_number = 1
    next_edge_number = 1

    skipped_invalid_count = 0
    skipped_duplicate_count = 0

    for feature_index, feature in enumerate(input_features, start=1):
        properties = feature.get("properties", {})
        geometry = feature.get("geometry")

        trail_id = properties.get("trail_id")
        if not trail_id:
            print(f"[건너뜀] trail_id 없음 - feature #{feature_index}")
            skipped_invalid_count += 1
            continue

        lines = split_to_lines(geometry)
        if not lines:
            print(f"[건너뜀] LineString/MultiLineString 아님 - trail_id={trail_id}")
            skipped_invalid_count += 1
            continue

        for segment_order, coords in enumerate(lines, start=1):
            if len(coords) < 2:
                print(f"[건너뜀] 좌표가 2개 미만 - trail_id={trail_id}, segment_order={segment_order}")
                skipped_invalid_count += 1
                continue

            # 좌표 반올림
            rounded_coords = [round_coord(c) for c in coords]

            # 중복 제거 (완전 동일 / 역방향 동일만 처리)
            line_key = make_line_key(rounded_coords)
            if line_key in seen_line_keys:
                print(f"[중복 제거] trail_id={trail_id}, segment_order={segment_order}")
                skipped_duplicate_count += 1
                continue
            seen_line_keys.add(line_key)

            # 길이 계산
            distance_m = calculate_length_m(rounded_coords)
            if distance_m <= 0:
                print(f"[건너뜀] 거리 0 이하 - trail_id={trail_id}, segment_order={segment_order}")
                skipped_invalid_count += 1
                continue

            # 시작 / 끝 좌표
            start_coord = rounded_coords[0]
            end_coord = rounded_coords[-1]

            # node 생성 또는 재사용
            start_node_id, next_node_number = get_or_create_node_id(
                start_coord,
                nodes_by_key,
                node_features,
                next_node_number
            )
            end_node_id, next_node_number = get_or_create_node_id(
                end_coord,
                nodes_by_key,
                node_features,
                next_node_number
            )

            # edge 생성
            edge_id = f"E{next_edge_number:04d}"
            next_edge_number += 1

            edge_feature = {
                "type": "Feature",
                "properties": {
                    "edge_id": edge_id,
                    "trail_id": trail_id,
                    "start_node_id": start_node_id,
                    "end_node_id": end_node_id,
                    "distance_m": distance_m,
                    "segment_order": segment_order,
                    "is_bidirectional": True,
                    "merge_status": "cleaned"  # 이 버전에서는 exact/reverse duplicate만 제거
                },
                "geometry": {
                    "type": "LineString",
                    "coordinates": rounded_coords
                }
            }
            edge_features.append(edge_feature)

            # node 연결 정보 누적
            node_edge_refs[start_node_id].add(edge_id)
            node_edge_refs[end_node_id].add(edge_id)

            node_trail_refs[start_node_id].add(trail_id)
            node_trail_refs[end_node_id].add(trail_id)

            node_start_count[start_node_id] += 1
            node_end_count[end_node_id] += 1

    # =========================
    # 4. node 후처리
    # =========================
    node_output_features: list[dict] = []

    for node_id in sorted(node_features.keys()):
        node_feature = node_features[node_id]

        degree = len(node_edge_refs[node_id])
        trail_refs = sorted(node_trail_refs[node_id])

        # 아주 단순한 첫 판별 규칙
        if degree >= 2:
            node_type = "junction"
        else:
            # degree == 1 일 때
            if node_start_count[node_id] > 0 and node_end_count[node_id] == 0:
                node_type = "start"
            elif node_end_count[node_id] > 0 and node_start_count[node_id] == 0:
                node_type = "end"
            else:
                # 애매하면 일단 end 처리
                node_type = "end"

        node_feature["properties"]["node_type"] = node_type
        node_feature["properties"]["degree"] = degree
        node_feature["properties"]["trail_refs"] = trail_refs

        node_output_features.append(node_feature)

    # edge도 ID 순으로 정렬
    edge_output_features = sorted(
        edge_features,
        key=lambda x: x["properties"]["edge_id"]
    )

    # 저장
    write_geojson(EDGES_OUTPUT_PATH, edge_output_features)
    write_geojson(NODES_OUTPUT_PATH, node_output_features)

    print("----- 처리 완료 -----")
    print(f"생성된 edge 수: {len(edge_output_features)}")
    print(f"생성된 node 수: {len(node_output_features)}")
    print(f"건너뛴 invalid 수: {skipped_invalid_count}")
    print(f"제거한 duplicate 수: {skipped_duplicate_count}")
    print(f"edges 저장 경로: {EDGES_OUTPUT_PATH}")
    print(f"nodes 저장 경로: {NODES_OUTPUT_PATH}")


if __name__ == "__main__":
    build_network()