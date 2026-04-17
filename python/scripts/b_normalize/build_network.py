from __future__ import annotations

import json
from collections import defaultdict, deque
from pathlib import Path

from pyproj import Geod


# =========================================================
# 1. 기본 설정
# =========================================================

PROJECT_ROOT = Path(__file__).resolve().parents[2]

INPUT_PATH = PROJECT_ROOT / "data" / "interim" / "a_output" / "standard_trail.geojson"
OUTPUT_DIR = PROJECT_ROOT / "data" / "interim" / "b_output"

EDGES_OUTPUT_PATH = OUTPUT_DIR / "trail_network_edges.geojson"
NODES_OUTPUT_PATH = OUTPUT_DIR / "trail_network_nodes.geojson"

GEOD = Geod(ellps="WGS84")
COORD_PRECISION = 7

PRUNE_ISOLATED_EDGE_MIN_M = 20.0
PRUNE_DANGLING_EDGE_MIN_M = 20.0

PRUNE_MAX_ITERATIONS = 50

DUPLICATE_EDGE_LENGTH_TOLERANCE_M = 5.0
MAX_EDGE_LENGTH_M = 50_000


# =========================================================
# 2. 공통 함수
# =========================================================

def read_geojson(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {path}")

    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if data.get("type") != "FeatureCollection":
        raise ValueError("입력 파일의 최상위 type은 FeatureCollection이어야 합니다.")

    return data


def write_geojson(path: Path, features: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    output = {
        "type": "FeatureCollection",
        "features": features
    }

    with path.open("w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)


def round_coord(coord: list[float], precision: int = COORD_PRECISION) -> list[float]:
    lng = round(float(coord[0]), precision)
    lat = round(float(coord[1]), precision)
    return [lng, lat]


def make_point_key(coord: list[float]) -> tuple[float, float]:
    c = round_coord(coord)
    return c[0], c[1]


def make_mountain_key(segment_or_edge: dict) -> str | None:
    raw_tags = segment_or_edge.get("raw_tags") or {}
    if isinstance(raw_tags, dict):
        mntn_code = raw_tags.get("MNTN_CODE") or raw_tags.get("MNTN_ID")
        if mntn_code is not None:
            text = str(mntn_code).strip()
            if text != "":
                return f"code:{text}"

    mountain_name = segment_or_edge.get("mountain_name")
    if mountain_name is not None:
        text = str(mountain_name).strip()
        if text != "":
            return f"name:{text}"

    return None


def make_line_key(
        coords: list[list[float]],
        is_bidirectional: bool = True,
        mountain_key: str | None = None,
) -> tuple:
    rounded = tuple(make_point_key(c) for c in coords)
    if is_bidirectional:
        key = min(rounded, tuple(reversed(rounded)))
    else:
        key = rounded

    # 같은 좌표라도 다른 산(혹은 산 식별 불가 그룹)은 다른 등산로로 취급
    return mountain_key, key


def make_node_pair_key(
        start_node_id: str,
        end_node_id: str,
        is_bidirectional: bool
) -> tuple[str, str] | tuple[str, str, str]:
    if is_bidirectional:
        return tuple(sorted([start_node_id, end_node_id]))
    return start_node_id, end_node_id, "directed"


def calculate_length_m(coords: list[list[float]]) -> float:
    lons = [c[0] for c in coords]
    lats = [c[1] for c in coords]
    length = GEOD.line_length(lons, lats)
    return round(abs(length), 1)


def split_to_lines(geometry: dict | None) -> list[list[list[float]]]:
    """
    geometry를 LineString 목록 형태로 통일해서 반환
    """
    if geometry is None:
        return []

    geom_type = geometry.get("type")
    coords = geometry.get("coordinates", [])

    if geom_type == "LineString":
        return [coords]

    if geom_type == "MultiLineString":
        return coords

    return []


def split_edge_coords_by_existing_nodes(
        coords: list[list[float]],
        registry: NodeRegistry,
        mountain_key: str | None,
) -> list[list[list[float]]]:

    if len(coords) < 2:
        return []

    split_indices = [0]

    for i in range(1, len(coords) - 1):
        node_id = registry.get_node_id_by_coord(coords[i], mountain_key)
        if node_id is not None:
            split_indices.append(i)

    split_indices.append(len(coords) - 1)

    segments: list[list[list[float]]] = []
    for start_idx, end_idx in zip(split_indices, split_indices[1:]):
        part = coords[start_idx:end_idx + 1]
        part = dedupe_consecutive_coords(part)
        if len(part) >= 2:
            segments.append(part)

    return segments


def collect_all_intersection_coords(
        edges: dict[str, dict]
) -> set[tuple[float, float, str | None]]:

    # 모든 엣지의 끝점 좌표를 키로 수집
    endpoint_keys: set[tuple[float, float, str | None]] = set()
    for edge in edges.values():
        mountain_key = edge.get("mountain_key")
        endpoint_keys.add((*make_point_key(edge["coords"][0]), mountain_key))
        endpoint_keys.add((*make_point_key(edge["coords"][-1]), mountain_key))

    # 어떤 엣지의 중간점이 다른 엣지의 끝점과 일치하면 교차점
    intersection_coords: set[tuple[float, float, str | None]] = set()
    for edge in edges.values():
        mountain_key = edge.get("mountain_key")
        for coord in edge["coords"][1:-1]:
            key = make_point_key(coord)
            composed = (*key, mountain_key)
            if composed in endpoint_keys:
                intersection_coords.add(composed)

    return intersection_coords


def split_edges_at_existing_nodes(
        edges: dict[str, dict],
        registry: NodeRegistry,
) -> tuple[dict[str, dict], dict]:

    intersection_coords = collect_all_intersection_coords(edges)
    for lon, lat, mountain_key in intersection_coords:
        registry.get_or_create([lon, lat], mountain_key)

    new_edges: dict[str, dict] = {}
    next_edge_number = max((int(eid[1:]) for eid in edges), default=0) + 1

    split_source_edge_count = 0
    created_split_edge_count = 0

    for edge_id in sorted(edges.keys()):
        edge = edges[edge_id]
        coords_parts = split_edge_coords_by_existing_nodes(
            edge["coords"],
            registry,
            edge.get("mountain_key")
        )

        if not coords_parts:
            continue

        if len(coords_parts) == 1:
            new_edges[edge_id] = edge
            continue

        split_source_edge_count += 1
        for part_coords in coords_parts:
            distance_m = calculate_length_m(part_coords)
            if distance_m <= 0:
                continue

            mountain_key = edge.get("mountain_key")
            start_node_id = registry.get_or_create(part_coords[0], mountain_key)
            end_node_id = registry.get_or_create(part_coords[-1], mountain_key)

            new_edge_id = f"E{next_edge_number:04d}"
            next_edge_number += 1

            new_edges[new_edge_id] = {
                "edge_id": new_edge_id,
                "trail_id": edge["trail_id"],
                "start_node_id": start_node_id,
                "end_node_id": end_node_id,
                "distance_m": distance_m,
                "source_segment_orders": list(edge["source_segment_orders"]),
                "is_bidirectional": edge["is_bidirectional"],
                "merge_status": edge["merge_status"],
                "coords": part_coords,
                "mountain_key": edge.get("mountain_key"),
                # 내부 비교용 메타데이터 유지
                "source": edge.get("source"),
                "source_ref": edge.get("source_ref"),
                "name": edge.get("name"),
                "surface": edge.get("surface"),
                "trail_type": edge.get("trail_type"),
                "mountain_name": edge.get("mountain_name"),
                "admin_region": edge.get("admin_region"),
                "is_official": edge.get("is_official"),
                "raw_tags": edge.get("raw_tags"),
            }
            created_split_edge_count += 1

    stats = {
        "split_source_edge_count": split_source_edge_count,
        "created_split_edge_count": created_split_edge_count,
    }
    return new_edges, stats


def dedupe_consecutive_coords(coords: list[list[float]]) -> list[list[float]]:
    """
    연속 중복 좌표 제거
    """
    if not coords:
        return []

    result = [coords[0]]
    for coord in coords[1:]:
        if make_point_key(coord) != make_point_key(result[-1]):
            result.append(coord)
    return result


def is_effectively_duplicate_edge(
        existing_edge: dict,
        start_node_id: str,
        end_node_id: str,
        distance_m: float,
        is_bidirectional: bool,
        tolerance_m: float = DUPLICATE_EDGE_LENGTH_TOLERANCE_M,
) -> bool:
    if existing_edge["is_bidirectional"] != is_bidirectional:
        return False

    existing_pair_key = make_node_pair_key(
        existing_edge["start_node_id"],
        existing_edge["end_node_id"],
        existing_edge["is_bidirectional"],
    )
    new_pair_key = make_node_pair_key(start_node_id, end_node_id, is_bidirectional)

    if existing_pair_key != new_pair_key:
        return False

    if abs(existing_edge["distance_m"] - distance_m) > tolerance_m:
        return False

    return True


def is_self_loop_edge(edge: dict) -> bool:
    return edge["start_node_id"] == edge["end_node_id"]


def normalize_optional_value(value):
    if value is None:
        return None
    if isinstance(value, str):
        stripped = value.strip()
        if stripped == "":
            return None
        return stripped
    return value


def get_source_priority(source: str | None) -> int:
    normalized = str(source).strip().upper() if source is not None else ""
    if normalized == "PUBLIC":
        return 2
    if normalized == "OSM":
        return 1
    return 0


def choose_preferred_edge(existing_edge: dict, incoming_edge: dict) -> dict:
    existing_priority = get_source_priority(existing_edge.get("source"))
    incoming_priority = get_source_priority(incoming_edge.get("source"))

    if incoming_priority > existing_priority:
        return incoming_edge
    if incoming_priority < existing_priority:
        return existing_edge

    existing_name = normalize_optional_value(existing_edge.get("name"))
    incoming_name = normalize_optional_value(incoming_edge.get("name"))
    if existing_name is None and incoming_name is not None:
        return incoming_edge

    existing_surface = normalize_optional_value(existing_edge.get("surface"))
    incoming_surface = normalize_optional_value(incoming_edge.get("surface"))
    if existing_surface is None and incoming_surface is not None:
        return incoming_edge

    return existing_edge


def should_replace_representative_edge(existing_edge: dict, candidate_edge: dict) -> bool:
    preferred = choose_preferred_edge(existing_edge, candidate_edge)
    if preferred is candidate_edge:
        return True
    if preferred is existing_edge:
        return False

    candidate_points = len(candidate_edge.get("coords", []))
    existing_points = len(existing_edge.get("coords", []))
    return candidate_points < existing_points


def merge_raw_tags(existing_raw_tags, incoming_raw_tags, existing_source=None, incoming_source=None):
    raw_tags1 = existing_raw_tags or {}
    raw_tags2 = incoming_raw_tags or {}

    if not isinstance(raw_tags1, dict) or not isinstance(raw_tags2, dict):
        return raw_tags1 or raw_tags2

    existing_priority = get_source_priority(existing_source)
    incoming_priority = get_source_priority(incoming_source)

    if incoming_priority > existing_priority:
        return {**raw_tags1, **raw_tags2}
    return {**raw_tags2, **raw_tags1}


def choose_preferred_value(
        existing_value,
        incoming_value,
        existing_source,
        incoming_source,
):
    value1 = normalize_optional_value(existing_value)
    value2 = normalize_optional_value(incoming_value)

    if value1 is None and value2 is None:
        return None
    if value1 is None:
        return value2
    if value2 is None:
        return value1

    existing_priority = get_source_priority(existing_source)
    incoming_priority = get_source_priority(incoming_source)

    if incoming_priority > existing_priority:
        return value2
    return value1


def merge_duplicate_edge_metadata(
        existing_edge: dict,
        incoming_data: dict,
) -> None:
    existing_source_priority = get_source_priority(existing_edge.get("source"))
    incoming_source_priority = get_source_priority(incoming_data.get("source"))

    existing_edge["name"] = choose_preferred_value(
        existing_edge.get("name"),
        incoming_data.get("name"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )
    existing_edge["surface"] = choose_preferred_value(
        existing_edge.get("surface"),
        incoming_data.get("surface"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )
    existing_edge["trail_type"] = choose_preferred_value(
        existing_edge.get("trail_type"),
        incoming_data.get("trail_type"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )
    existing_edge["mountain_name"] = choose_preferred_value(
        existing_edge.get("mountain_name"),
        incoming_data.get("mountain_name"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )
    existing_edge["admin_region"] = choose_preferred_value(
        existing_edge.get("admin_region"),
        incoming_data.get("admin_region"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )

    existing_edge["raw_tags"] = merge_raw_tags(
        existing_edge.get("raw_tags"),
        incoming_data.get("raw_tags"),
        existing_edge.get("source"),
        incoming_data.get("source"),
    )

    if incoming_source_priority > existing_source_priority:
        existing_edge["trail_id"] = incoming_data.get("trail_id", existing_edge["trail_id"])
        existing_edge["source"] = incoming_data.get("source")
        existing_edge["source_ref"] = incoming_data.get("source_ref")
        existing_edge["is_official"] = incoming_data.get("is_official")
    else:
        existing_edge["source_ref"] = choose_preferred_value(
            existing_edge.get("source_ref"),
            incoming_data.get("source_ref"),
            existing_edge.get("source"),
            incoming_data.get("source"),
        )
        existing_edge["is_official"] = choose_preferred_value(
            existing_edge.get("is_official"),
            incoming_data.get("is_official"),
            existing_edge.get("source"),
            incoming_data.get("source"),
        )

    incoming_segment_order = incoming_data.get("source_segment_order")
    incoming_segment_orders = incoming_data.get("source_segment_orders", [])

    merged_orders = set(existing_edge.get("source_segment_orders", []))
    if incoming_segment_order is not None:
        merged_orders.add(incoming_segment_order)
    merged_orders.update(incoming_segment_orders)
    existing_edge["source_segment_orders"] = sorted(merged_orders)


# =========================================================
# 3. 노드 레지스트리
# =========================================================

class NodeRegistry:
    def __init__(self) -> None:
        self._key_to_id: dict[tuple[float, float, str | None], str] = {}
        self._id_to_coord: dict[str, list[float]] = {}
        self._counter = 1

    def get_or_create(self, coord: list[float], mountain_key: str | None = None) -> str:
        key = (*make_point_key(coord), mountain_key)
        if key not in self._key_to_id:
            node_id = f"N{self._counter:04d}"
            self._counter += 1
            self._key_to_id[key] = node_id
            self._id_to_coord[node_id] = [key[0], key[1]]
        return self._key_to_id[key]

    def coord(self, node_id: str) -> list[float]:
        return self._id_to_coord[node_id]

    def get_node_id_by_coord(self, coord: list[float], mountain_key: str | None = None) -> str | None:
        key = (*make_point_key(coord), mountain_key)
        return self._key_to_id.get(key)


# =========================================================
# 4. 입력 정규화
# =========================================================

def normalize_input_features(data: dict) -> tuple[list[dict], dict]:
    """
    A 단계 산출물을 B 단계에서 처리하기 쉽게 정규화한다.
    - FeatureCollection 검증
    - trail_id / source / geometry 확인
    - LineString / MultiLineString만 허용
    - length_m는 믿지 않고 B에서 다시 계산
    """
    features = data.get("features", [])

    normalized_segments: list[dict] = []

    stats = {
        "input_feature_count": len(features),
        "invalid_missing_required": 0,
        "invalid_geometry": 0,
        "invalid_short_coords": 0,
    }

    for feature_index, feature in enumerate(features, start=1):
        properties = feature.get("properties", {}) or {}
        geometry = feature.get("geometry")

        trail_id = properties.get("trail_id")
        source = properties.get("source")

        if not trail_id or not source or geometry is None:
            print(f"[건너뜀] 필수값 누락 - feature #{feature_index}")
            stats["invalid_missing_required"] += 1
            continue

        lines = split_to_lines(geometry)
        if not lines:
            print(f"[건너뜀] geometry가 LineString/MultiLineString 아님 - trail_id={trail_id}")
            stats["invalid_geometry"] += 1
            continue

        for segment_order, coords in enumerate(lines, start=1):
            if len(coords) < 2:
                print(f"[건너뜀] 좌표가 2개 미만 - trail_id={trail_id}, segment_order={segment_order}")
                stats["invalid_short_coords"] += 1
                continue

            rounded_coords = [round_coord(c) for c in coords]

            raw_tags = properties.get("raw_tags")
            segment_mountain_key = make_mountain_key({
                "raw_tags": raw_tags,
                "mountain_name": properties.get("mountain_name"),
            })

            normalized_segments.append({
                "trail_id": trail_id,
                "source": source,
                "source_ref": properties.get("source_ref"),
                "name": properties.get("name"),
                "surface": properties.get("surface"),
                "trail_type": properties.get("trail_type"),
                "mountain_name": properties.get("mountain_name"),
                "admin_region": properties.get("admin_region"),
                "is_official": properties.get("is_official"),
                "raw_tags": properties.get("raw_tags"),
                "mountain_key": segment_mountain_key,
                "is_bidirectional": properties.get("is_bidirectional", True),
                "source_segment_order": segment_order,
                "coords": rounded_coords,
            })

    return normalized_segments, stats


# =========================================================
# 5. 초기 그래프 생성
# =========================================================

def build_initial_graph(
        segments: list[dict],
        registry: NodeRegistry,
) -> tuple[dict[str, dict], dict]:

    edges: dict[str, dict] = {}
    seen_edges_by_line_key: dict[tuple, dict] = {}
    seen_edges_by_node_pair: dict[tuple, dict] = {}
    next_edge_number = 1

    stats = {
        "duplicate_removed_count": 0,
        "invalid_zero_length": 0,
    }

    for segment in segments:
        coords = dedupe_consecutive_coords(segment["coords"])
        if len(coords) < 2:
            stats["invalid_zero_length"] += 1
            continue

        distance_m = calculate_length_m(coords)
        if distance_m <= 0:
            print(f"[건너뜀] 거리 0 이하 - trail_id={segment['trail_id']}, segment_order={segment['source_segment_order']}")
            stats["invalid_zero_length"] += 1
            continue

        mountain_key = segment.get("mountain_key")
        line_key = make_line_key(coords, segment["is_bidirectional"], mountain_key=mountain_key)
        existing_line_edge = seen_edges_by_line_key.get(line_key)
        if existing_line_edge is not None:
            print(f"[중복 제거: 동일 좌표] trail_id={segment['trail_id']}, segment_order={segment['source_segment_order']}")
            merge_duplicate_edge_metadata(existing_line_edge, segment)
            stats["duplicate_removed_count"] += 1
            continue

        start_node_id = registry.get_or_create(coords[0], mountain_key)
        end_node_id = registry.get_or_create(coords[-1], mountain_key)

        node_pair_key = make_node_pair_key(
            start_node_id,
            end_node_id,
            segment["is_bidirectional"],
        )

        existing_edge = seen_edges_by_node_pair.get(node_pair_key)
        if existing_edge and is_effectively_duplicate_edge(
                existing_edge=existing_edge,
                start_node_id=start_node_id,
                end_node_id=end_node_id,
                distance_m=distance_m,
                is_bidirectional=segment["is_bidirectional"],
        ):
            print(f"[중복 제거: 동일 노드쌍] trail_id={segment['trail_id']}, segment_order={segment['source_segment_order']}")
            merge_duplicate_edge_metadata(existing_edge, segment)
            stats["duplicate_removed_count"] += 1
            continue

        edge_id = f"E{next_edge_number:04d}"
        next_edge_number += 1

        edge = {
            "edge_id": edge_id,
            "trail_id": segment["trail_id"],
            "start_node_id": start_node_id,
            "end_node_id": end_node_id,
            "distance_m": distance_m,
            "source_segment_orders": [segment["source_segment_order"]],
            "is_bidirectional": segment["is_bidirectional"],
            "merge_status": "cleaned",
            "coords": coords,
            "mountain_key": mountain_key,
            # 내부 비교용 메타데이터 (surface는 최종 output에도 포함)
            "source": segment["source"],
            "source_ref": segment["source_ref"],
            "name": segment["name"],
            "surface": segment["surface"],
            "trail_type": segment["trail_type"],
            "mountain_name": segment["mountain_name"],
            "admin_region": segment["admin_region"],
            "is_official": segment["is_official"],
            "raw_tags": segment["raw_tags"],
        }
        edges[edge_id] = edge
        seen_edges_by_line_key[line_key] = edge

        saved_edge = seen_edges_by_node_pair.get(node_pair_key)
        if saved_edge is None or should_replace_representative_edge(saved_edge, edge):
            seen_edges_by_node_pair[node_pair_key] = edge

    return edges, stats


def deduplicate_edges_after_split(
        edges: dict[str, dict],
) -> tuple[dict[str, dict], dict]:
    deduped_edges: dict[str, dict] = {}
    seen_edges_by_line_key: dict[tuple, dict] = {}
    seen_edges_by_node_pair: dict[tuple, dict] = {}

    stats = {
        "post_split_duplicate_removed_count": 0,
        "post_split_same_line_removed_count": 0,
        "post_split_same_node_pair_removed_count": 0,
    }

    for edge_id in sorted(edges.keys()):
        edge = edges[edge_id]

        line_key = make_line_key(edge["coords"], edge["is_bidirectional"], mountain_key=edge.get("mountain_key"))
        existing_line_edge = seen_edges_by_line_key.get(line_key)
        if existing_line_edge is not None:
            merge_duplicate_edge_metadata(existing_line_edge, edge)
            stats["post_split_duplicate_removed_count"] += 1
            stats["post_split_same_line_removed_count"] += 1
            continue

        node_pair_key = make_node_pair_key(
            edge["start_node_id"],
            edge["end_node_id"],
            edge["is_bidirectional"],
        )

        existing_edge = seen_edges_by_node_pair.get(node_pair_key)
        if existing_edge and is_effectively_duplicate_edge(
                existing_edge=existing_edge,
                start_node_id=edge["start_node_id"],
                end_node_id=edge["end_node_id"],
                distance_m=edge["distance_m"],
                is_bidirectional=edge["is_bidirectional"],
        ):
            merge_duplicate_edge_metadata(existing_edge, edge)
            stats["post_split_duplicate_removed_count"] += 1
            stats["post_split_same_node_pair_removed_count"] += 1
            continue

        deduped_edges[edge_id] = edge
        seen_edges_by_line_key[line_key] = edge

        saved_edge = seen_edges_by_node_pair.get(node_pair_key)
        if saved_edge is None or should_replace_representative_edge(saved_edge, edge):
            seen_edges_by_node_pair[node_pair_key] = edge

    return deduped_edges, stats


def build_node_to_edges(edges: dict[str, dict]) -> dict[str, set[str]]:
    node_to_edges: defaultdict[str, set[str]] = defaultdict(set)

    for edge_id, edge in edges.items():
        node_to_edges[edge["start_node_id"]].add(edge_id)
        node_to_edges[edge["end_node_id"]].add(edge_id)

    return dict(node_to_edges)


def build_node_to_trails(edges: dict[str, dict]) -> dict[str, set[str]]:
    node_to_trails: defaultdict[str, set[str]] = defaultdict(set)

    for edge in edges.values():
        node_to_trails[edge["start_node_id"]].add(edge["trail_id"])
        node_to_trails[edge["end_node_id"]].add(edge["trail_id"])

    return dict(node_to_trails)


# =========================================================
# 6. degree=2 병합 판정 / 병합
# =========================================================

def should_collapse_degree2_node(
        node_id: str,
        connected_edge_ids: list[str],
        edges: dict[str, dict]
) -> bool:
    """
    의미 없는 degree=2 노드만 병합 대상으로 본다.
    굉장히 보수적으로 판단한다.
    """
    if len(connected_edge_ids) != 2:
        return False

    edge1 = edges[connected_edge_ids[0]]
    edge2 = edges[connected_edge_ids[1]]

    # 같은 edge 두 번 연결된 이상한 상황 방지
    if edge1["edge_id"] == edge2["edge_id"]:
        return False

    # self-loop가 하나라도 끼어 있으면 병합 금지
    if is_self_loop_edge(edge1) or is_self_loop_edge(edge2):
        return False

    # 이동 방향 정책이 다르면 유지
    if edge1["is_bidirectional"] != edge2["is_bidirectional"]:
        return False

    # 핵심 속성이 다르면 경계점으로 보고 유지
    compare_fields = [
        "source",
        "trail_type",
        "mountain_name",
        "admin_region",
        "is_official",
    ]
    for field in compare_fields:
        if edge1.get(field) != edge2.get(field):
            return False

    surface1 = normalize_optional_value(edge1.get("surface"))
    surface2 = normalize_optional_value(edge2.get("surface"))
    if surface1 is not None and surface2 is not None and surface1 != surface2:
        return False

    # 양옆이 같은 노드로 연결되면(루프 형태) 병합하지 않음
    other1 = edge1["end_node_id"] if edge1["start_node_id"] == node_id else edge1["start_node_id"]
    other2 = edge2["end_node_id"] if edge2["start_node_id"] == node_id else edge2["start_node_id"]
    if other1 == other2:
        return False

    return True


def orient_edge_coords_toward_node(edge: dict, node_id: str) -> tuple[list[list[float]], str]:
    """
    edge의 좌표를 '다른 노드 -> node_id' 방향으로 맞춘다.
    반환:
    - 정렬된 좌표
    - 다른 쪽 노드 ID
    """
    if edge["start_node_id"] == edge["end_node_id"]:
        raise ValueError(f"self-loop 엣지 {edge['edge_id']}는 병합 불가.")

    if edge["end_node_id"] == node_id:
        return edge["coords"], edge["start_node_id"]

    if edge["start_node_id"] == node_id:
        return list(reversed(edge["coords"])), edge["end_node_id"]

    raise ValueError(f"edge {edge['edge_id']}는 node {node_id}와 연결되어 있지 않습니다.")


def orient_edge_coords_from_node(edge: dict, node_id: str) -> tuple[list[list[float]], str]:
    """
    edge의 좌표를 'node_id -> 다른 노드' 방향으로 맞춘다.
    반환:
    - 정렬된 좌표
    - 다른 쪽 노드 ID
    """
    if edge["start_node_id"] == edge["end_node_id"]:
        raise ValueError(f"self-loop 엣지 {edge['edge_id']}는 병합 불가.")

    if edge["start_node_id"] == node_id:
        return edge["coords"], edge["end_node_id"]

    if edge["end_node_id"] == node_id:
        return list(reversed(edge["coords"])), edge["start_node_id"]

    raise ValueError(f"edge {edge['edge_id']}는 node {node_id}와 연결되어 있지 않습니다.")


def merge_two_edges_through_node(
        node_id: str,
        edge1: dict,
        edge2: dict,
        new_edge_id: str
) -> dict:
    """
    node_id를 가운데 두고 edge1 + edge2를 하나의 edge로 병합
    """

    coords1, other_start_node_id = orient_edge_coords_toward_node(edge1, node_id)
    coords2, other_end_node_id = orient_edge_coords_from_node(edge2, node_id)

    merged_coords = coords1 + coords2[1:]
    merged_coords = dedupe_consecutive_coords(merged_coords)

    distance_m = calculate_length_m(merged_coords)

    preferred_edge = choose_preferred_edge(edge1, edge2)

    merged_raw_tags = merge_raw_tags(
        edge1.get("raw_tags"),
        edge2.get("raw_tags"),
        edge1.get("source"),
        edge2.get("source"),
    )
    merged_surface = choose_preferred_value(
        edge1.get("surface"),
        edge2.get("surface"),
        edge1.get("source"),
        edge2.get("source"),
    )

    mountain_key1 = edge1.get("mountain_key")
    mountain_key2 = edge2.get("mountain_key")
    merged_mountain_key = mountain_key1 if mountain_key1 == mountain_key2 else (mountain_key1 or mountain_key2)

    return {
        "edge_id": new_edge_id,
        "trail_id": preferred_edge["trail_id"],
        "start_node_id": other_start_node_id,
        "end_node_id": other_end_node_id,
        "distance_m": distance_m,
        "source_segment_orders": sorted(
            edge1["source_segment_orders"] + edge2["source_segment_orders"]
        ),
        "is_bidirectional": edge1["is_bidirectional"],
        "merge_status": "merged",
        "coords": merged_coords,
        "mountain_key": merged_mountain_key,
        # 내부 비교용 메타데이터는 하나로 유지
        "source": preferred_edge.get("source"),
        "source_ref": preferred_edge.get("source_ref"),
        "name": choose_preferred_value(
            edge1.get("name"),
            edge2.get("name"),
            edge1.get("source"),
            edge2.get("source"),
        ),
        "surface": merged_surface,
        "trail_type": choose_preferred_value(
            edge1.get("trail_type"),
            edge2.get("trail_type"),
            edge1.get("source"),
            edge2.get("source"),
        ),
        "mountain_name": choose_preferred_value(
            edge1.get("mountain_name"),
            edge2.get("mountain_name"),
            edge1.get("source"),
            edge2.get("source"),
        ),
        "admin_region": choose_preferred_value(
            edge1.get("admin_region"),
            edge2.get("admin_region"),
            edge1.get("source"),
            edge2.get("source"),
        ),
        "is_official": choose_preferred_value(
            edge1.get("is_official"),
            edge2.get("is_official"),
            edge1.get("source"),
            edge2.get("source"),
        ),
        "raw_tags": merged_raw_tags,
    }


def collapse_pass_through_nodes(
        edges: dict[str, dict]
) -> tuple[dict[str, dict], dict]:
    """
    degree=2 중 의미 없는 노드를 반복적으로 병합 제거
    후보 노드들을 deque에 넣어 deque가 빌 때까지 반복하는 방식으로 변경
    """
    node_to_edges = build_node_to_edges(edges)

    # 초기 degree=2 후보
    queue: deque[str] = deque(
        node_id for node_id, eids in node_to_edges.items()
        if len(eids) == 2
    )
    in_queue: set[str] = set(queue)

    next_edge_num = (
        max(int(eid[1:]) for eid in edges) + 1 if edges else 1
    )
    stats = {
        "collapsed_degree2_node_count": 0,
        "merged_edge_count": 0,
    }

    while queue:
        node_id = queue.popleft()
        in_queue.discard(node_id)

        connected = sorted(node_to_edges.get(node_id, set()))

        # 실제 병합 조건 재확인 (다른 병합으로 상황이 바뀌었을 수 있음)
        if len(connected) != 2:
            continue

        e1 = edges[connected[0]]
        e2 = edges[connected[1]]

        # self-loop 엣지가 끼어 있으면 병합 후보에서 제외
        if is_self_loop_edge(e1) or is_self_loop_edge(e2):
            continue

        if not should_collapse_degree2_node(node_id, connected, edges):
            continue

        new_edge_id = f"E{next_edge_num:04d}"
        next_edge_num += 1

        merged = merge_two_edges_through_node(node_id, e1, e2, new_edge_id)

        old_edge_ids = [e1["edge_id"], e2["edge_id"]]
        touched_node_ids = {
            e1["start_node_id"], e1["end_node_id"],
            e2["start_node_id"], e2["end_node_id"],
        }

        # 기존 엣지 제거
        for old_eid in old_edge_ids:
            if old_eid in edges:
                del edges[old_eid]

        for nid in touched_node_ids:
            if nid in node_to_edges:
                for old_eid in old_edge_ids:
                    node_to_edges[nid].discard(old_eid)

        # 새 엣지 등록
        edges[new_edge_id] = merged
        node_to_edges.setdefault(merged["start_node_id"], set()).add(new_edge_id)
        node_to_edges.setdefault(merged["end_node_id"], set()).add(new_edge_id)

        # 병합된 노드 제거
        node_to_edges.pop(node_id, None)

        stats["collapsed_degree2_node_count"] += 1
        stats["merged_edge_count"] += 1

        # 새 엣지 양 끝을 재검사 후보로 추가
        for neighbor in [merged["start_node_id"], merged["end_node_id"]]:
            if neighbor not in in_queue:
                queue.append(neighbor)
                in_queue.add(neighbor)

    return edges, stats


# =========================================================
# 7. Dangling 엣지 제거
# =========================================================

def prune_dangling_edges(
        edges: dict[str, dict],
        isolated_min_m: float = PRUNE_ISOLATED_EDGE_MIN_M,
        dangling_min_m: float = PRUNE_DANGLING_EDGE_MIN_M,
        max_iterations: int = PRUNE_MAX_ITERATIONS,
) -> tuple[dict[str, dict], dict]:
    """
    네트워크와 연결되지 않은 짧은 엣지를 제거한다.
    완전 고립, 막다른 길
    """
    def is_prunable(edge: dict) -> bool:
        source = str(edge.get("source") or "").strip().upper()
        is_official = edge.get("is_official") is True
        # 공공/공식 데이터는 최대한 보존 (산 분리 후 단절되어도 제거하지 않음)
        if source == "PUBLIC" or is_official:
            return False
        return True

    stats = {
        "isolated_removed_count": 0,
        "dangling_removed_count": 0,
        "iterations": 0,
    }

    # ── 케이스 1: 완전 고립 엣지 (1회)
    node_to_edges = build_node_to_edges(edges)
    to_remove: list[str] = []

    for edge_id, edge in edges.items():
        if not is_prunable(edge):
            continue
        s_deg = len(node_to_edges.get(edge["start_node_id"], set()))
        e_deg = len(node_to_edges.get(edge["end_node_id"], set()))
        if s_deg == 1 and e_deg == 1 and edge["distance_m"] < isolated_min_m:
            to_remove.append(edge_id)

    for edge_id in to_remove:
        del edges[edge_id]
    stats["isolated_removed_count"] = len(to_remove)

    # ── 케이스 2: 막다른 엣지 (반복)
    for _ in range(max_iterations):
        stats["iterations"] += 1
        node_to_edges = build_node_to_edges(edges)
        to_remove = []

        for edge_id, edge in edges.items():
            if not is_prunable(edge):
                continue
            s_deg = len(node_to_edges.get(edge["start_node_id"], set()))
            e_deg = len(node_to_edges.get(edge["end_node_id"], set()))
            # 한쪽만 degree=1 이고 기준 거리 미만
            is_dangling = (s_deg == 1) != (e_deg == 1)
            if is_dangling and edge["distance_m"] < dangling_min_m:
                to_remove.append(edge_id)

        if not to_remove:
            break

        for edge_id in to_remove:
            del edges[edge_id]
        stats["dangling_removed_count"] += len(to_remove)

    return edges, stats


# =========================================================
# 8. 최종 output 재구성
# =========================================================

def build_final_output_features(
        edges: dict[str, dict],
        registry: NodeRegistry
) -> tuple[list[dict], list[dict], dict]:
    node_to_edges = build_node_to_edges(edges)
    node_to_trails = build_node_to_trails(edges)

    # 최종 edge feature
    edge_features: list[dict] = []
    for edge_id in sorted(edges.keys()):
        edge = edges[edge_id]

        # 최종 규약상 segment_order는 단일값이므로
        # 병합 전 원본 순번들 중 최소값을 사용
        segment_order = min(edge["source_segment_orders"]) if edge["source_segment_orders"] else 1

        edge_features.append({
            "type": "Feature",
            "properties": {
                "edge_id": edge["edge_id"],
                "trail_id": edge["trail_id"],
                "surface": edge.get("surface"),
                "start_node_id": edge["start_node_id"],
                "end_node_id": edge["end_node_id"],
                "distance_m": edge["distance_m"],
                "segment_order": segment_order,
                "is_bidirectional": edge["is_bidirectional"],
                "merge_status": edge["merge_status"],
            },
            "geometry": {
                "type": "LineString",
                "coordinates": edge["coords"]
            }
        })

    # 최종 node feature
    node_features: list[dict] = []
    active_node_ids = sorted(node_to_edges.keys())

    remaining_degree2_count = 0

    for node_id in active_node_ids:
        degree = len(node_to_edges[node_id])
        trail_refs = sorted(list(node_to_trails[node_id]))

        # 규약상 node_type은 start / end / junction / summit_access
        # degree=2는 웬만하면 collapse했지만, 병합 불가로 남은 경우가 있을 수 있음.
        # 현재 규약에는 connector/boundary가 없으므로 실무적으로 junction로 둔다.
        if degree >= 3:
            node_type = "junction"
        elif degree == 2:
            node_type = "junction"
            remaining_degree2_count += 1
        else:
            # degree == 1
            connected_edge = edges[next(iter(node_to_edges[node_id]))]

            if connected_edge["is_bidirectional"]:
                # 양방향 엣지는 방향성이 없으므로 start/end를 명확히 구분할 수 없음.
                # 규약상 endpoint가 없으므로 기존 원칙대로 start로 통일.
                node_type = "start"
            else:
                # 단방향 엣지는 실제 연결 방향으로 start/end 판별
                if connected_edge["start_node_id"] == node_id:
                    node_type = "start"
                else:
                    node_type = "end"

        node_features.append({
            "type": "Feature",
            "properties": {
                "node_id": node_id,
                "node_type": node_type,
                "degree": degree,
                "trail_refs": trail_refs,
            },
            "geometry": {
                "type": "Point",
                "coordinates": registry.coord(node_id)
            }
        })

    stats = {
        "final_edge_count": len(edge_features),
        "final_node_count": len(node_features),
        "remaining_degree2_node_count": remaining_degree2_count,
    }

    return edge_features, node_features, stats


# =========================================================
# 9. QA
# =========================================================

def run_basic_qa(edge_features: list[dict], node_features: list[dict], registry: NodeRegistry) -> None:
    node_ids = {feature["properties"]["node_id"] for feature in node_features}

    missing_node_ref_count = 0
    non_positive_distance_count = 0
    self_loop_count = 0
    coord_mismatch_count = 0
    abnormal_length_count = 0

    for edge_feature in edge_features:
        props = edge_feature["properties"]
        coords = edge_feature["geometry"]["coordinates"]

        start_node_id = props["start_node_id"]
        end_node_id = props["end_node_id"]

        if props["start_node_id"] not in node_ids or props["end_node_id"] not in node_ids:
            missing_node_ref_count += 1

        if props["distance_m"] <= 0:
            non_positive_distance_count += 1
        if start_node_id == end_node_id:
            self_loop_count += 1
        if props["distance_m"] > MAX_EDGE_LENGTH_M:
            abnormal_length_count += 1

        try:
            expected_start = registry.coord(start_node_id)
            expected_end = registry.coord(end_node_id)

            if (
                    make_point_key(coords[0]) != make_point_key(expected_start)
                    or make_point_key(coords[-1]) != make_point_key(expected_end)
            ):
                coord_mismatch_count += 1
        except KeyError:
            # 이미 node 참조 누락으로 집계된 경우
            pass

    print("----- QA 결과 -----")
    print(f"node 참조 누락 edge 수: {missing_node_ref_count}")
    print(f"distance_m <= 0 edge 수: {non_positive_distance_count}")
    print(f"self-loop edge 수: {self_loop_count}")
    print(f"coords-노드 좌표 불일치 edge 수: {coord_mismatch_count}")
    print(f"비정상 장거리 edge 수 ({MAX_EDGE_LENGTH_M/1000:.0f}km 초과, 좌표 오류 의심): {abnormal_length_count}")


def report_mixed_mountain_nodes(edges: dict[str, dict]) -> None:
    node_to_edges = build_node_to_edges(edges)
    mixed_count = 0

    for node_id, edge_ids in node_to_edges.items():
        mountain_keys = {edges[eid].get("mountain_key") for eid in edge_ids}
        mountain_keys.discard(None)
        if len(mountain_keys) > 1:
            mixed_count += 1

    print("----- 산 코드 혼합 노드 점검 -----")
    print(f"서로 다른 mountain_key가 섞인 node 수: {mixed_count}")


# =========================================================
# 10. 실행
# =========================================================

def build_network() -> None:
    data = read_geojson(INPUT_PATH)

    normalized_segments, normalize_stats = normalize_input_features(data)
    print(f"입력 feature 수: {normalize_stats['input_feature_count']}")
    print(f"필수값 누락 제거: {normalize_stats['invalid_missing_required']}")
    print(f"geometry 오류 제거: {normalize_stats['invalid_geometry']}")
    print(f"좌표 부족 제거: {normalize_stats['invalid_short_coords']}")
    print(f"정규화된 라인 수: {len(normalized_segments)}")

    registry = NodeRegistry()
    edges, initial_stats = build_initial_graph(normalized_segments, registry)
    initial_node_count = len(build_node_to_edges(edges))

    print("----- 초기 그래프 생성 완료 -----")
    print(f"초기 edge 수: {len(edges)}")
    print(f"초기 node 수: {initial_node_count}")
    print(f"중복 제거 수: {initial_stats['duplicate_removed_count']}")
    print(f"거리 0 제거 수: {initial_stats['invalid_zero_length']}")

    edges, split_stats = split_edges_at_existing_nodes(edges, registry)

    print("----- 기존 node 기준 edge 분할 완료 -----")
    print(f"분할 대상 원본 edge 수: {split_stats['split_source_edge_count']}")
    print(f"분할 후 생성된 edge 수: {split_stats['created_split_edge_count']}")

    edges, post_split_dedup_stats = deduplicate_edges_after_split(edges)

    print("----- 분할 후 재중복 제거 완료 -----")
    print(f"분할 후 중복 제거 수: {post_split_dedup_stats['post_split_duplicate_removed_count']}")
    print(f"동일 좌표 재중복 제거 수: {post_split_dedup_stats['post_split_same_line_removed_count']}")
    print(f"동일 노드쌍 재중복 제거 수: {post_split_dedup_stats['post_split_same_node_pair_removed_count']}")

    total_collapse_stats = {"collapsed_degree2_node_count": 0, "merged_edge_count": 0}
    total_prune_stats = {"isolated_removed_count": 0, "dangling_removed_count": 0, "iterations": 0}
    loop_count = 0

    while True:
        loop_count += 1

        edges, collapse_stats = collapse_pass_through_nodes(edges)
        total_collapse_stats["collapsed_degree2_node_count"] += collapse_stats["collapsed_degree2_node_count"]
        total_collapse_stats["merged_edge_count"] += collapse_stats["merged_edge_count"]

        if collapse_stats["collapsed_degree2_node_count"] > 0:
            edges, _ = deduplicate_edges_after_split(edges)

        edges, prune_stats = prune_dangling_edges(edges)
        total_prune_stats["isolated_removed_count"] += prune_stats["isolated_removed_count"]
        total_prune_stats["dangling_removed_count"] += prune_stats["dangling_removed_count"]
        total_prune_stats["iterations"] += prune_stats["iterations"]

        if (
                collapse_stats["collapsed_degree2_node_count"] == 0
                and prune_stats["isolated_removed_count"] == 0
                and prune_stats["dangling_removed_count"] == 0
        ):
            break

    print(f"----- degree=2 병합 + Dangling 제거 완료 ({loop_count}회 수렴) -----")
    print(f"collapse된 degree=2 node 수 : {total_collapse_stats['collapsed_degree2_node_count']}")
    print(f"병합된 edge 생성 수 : {total_collapse_stats['merged_edge_count']}")
    print(f"완전 고립 엣지 ({PRUNE_ISOLATED_EDGE_MIN_M}m) 제거 수 : {total_prune_stats['isolated_removed_count']}")
    print(f"막다른 엣지 ({PRUNE_DANGLING_EDGE_MIN_M}m) 제거 수 : {total_prune_stats['dangling_removed_count']}")

    report_mixed_mountain_nodes(edges)

    edge_features, node_features, final_stats = build_final_output_features(edges, registry)

    write_geojson(EDGES_OUTPUT_PATH, edge_features)
    write_geojson(NODES_OUTPUT_PATH, node_features)

    run_basic_qa(edge_features, node_features, registry)

    print("----- 최종 완료 -----")
    print(f"최종 edge 수: {final_stats['final_edge_count']}")
    print(f"최종 node 수: {final_stats['final_node_count']}")
    print(f"병합 불가로 남은 degree=2 node 수: {final_stats['remaining_degree2_node_count']}")
    print(f"edges 저장 경로: {EDGES_OUTPUT_PATH}")
    print(f"nodes 저장 경로: {NODES_OUTPUT_PATH}")


if __name__ == "__main__":
    build_network()