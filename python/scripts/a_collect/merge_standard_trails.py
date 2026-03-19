from __future__ import annotations

import hashlib
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from pyproj import Transformer
from shapely.geometry import shape
from shapely.ops import transform, unary_union


REQUIRED_PROPERTIES = [
    "trail_id",
    "name",
    "source",
    "source_ref",
    "length_m",
    "surface",
    "trail_type",
    "mountain_name",
    "admin_region",
    "is_official",
    "raw_tags",
]

DISTANCE_TOLERANCE_M = 25.0
COVERAGE_THRESHOLD = 0.75
GRID_SIZE_M = 500.0

SCRIPT_PATH = Path(__file__).resolve()
PYTHON_DIR = SCRIPT_PATH.parents[2]
A_OUTPUT_DIR = PYTHON_DIR / "data" / "interim" / "a_output"

OSM_INPUT_PATH = A_OUTPUT_DIR / "standard_osm_trail.geojson"
PUBLIC_INPUT_PATH = A_OUTPUT_DIR / "standard_public_trail.geojson"
OUTPUT_PATH = A_OUTPUT_DIR / "standard_trail.geojson"


def read_json(path: Path) -> Any:
    if not path.exists():
        raise FileNotFoundError(f"파일이 없습니다: {path}")
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as file:
        json.dump(
            data,
            file,
            ensure_ascii=False,
            separators=(",", ":"),
        )


def validate_feature_collection(data: dict[str, Any], label: str) -> list[dict[str, Any]]:
    if data.get("type") != "FeatureCollection":
        raise ValueError(f"{label}: type이 FeatureCollection이 아닙니다.")

    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError(f"{label}: features가 리스트가 아닙니다.")

    for index, feature in enumerate(features):
        if feature.get("type") != "Feature":
            raise ValueError(f"{label}: feature[{index}] type이 Feature가 아닙니다.")

        geometry = feature.get("geometry")
        if not isinstance(geometry, dict):
            raise ValueError(f"{label}: feature[{index}] geometry가 dict가 아닙니다.")

        if geometry.get("type") != "LineString":
            raise ValueError(f"{label}: feature[{index}] geometry.type이 LineString이 아닙니다.")

        coordinates = geometry.get("coordinates")
        if not isinstance(coordinates, list) or len(coordinates) < 2:
            raise ValueError(f"{label}: feature[{index}] coordinates가 올바르지 않습니다.")

        for coord_index, coord in enumerate(coordinates):
            if not isinstance(coord, list) or len(coord) != 2:
                raise ValueError(
                    f"{label}: feature[{index}] coordinates[{coord_index}]가 [lon, lat] 형태가 아닙니다."
                )

            lon, lat = coord
            if not isinstance(lon, (int, float)) or not isinstance(lat, (int, float)):
                raise ValueError(
                    f"{label}: feature[{index}] coordinates[{coord_index}]에 숫자가 아닌 값이 있습니다."
                )

            if not (-180 <= lon <= 180 and -90 <= lat <= 90):
                raise ValueError(
                    f"{label}: feature[{index}] coordinates[{coord_index}] 범위가 잘못되었습니다."
                )

        properties = feature.get("properties")
        if not isinstance(properties, dict):
            raise ValueError(f"{label}: feature[{index}] properties가 dict가 아닙니다.")

        for key in REQUIRED_PROPERTIES:
            if key not in properties:
                raise ValueError(f"{label}: feature[{index}] properties에 필수 키 {key} 가 없습니다.")

    return features


def normalize_feature(feature: dict[str, Any], source_label: str) -> dict[str, Any]:
    properties = dict(feature["properties"])
    geometry = feature["geometry"]

    original_trail_id = str(properties.get("trail_id", "")).strip()
    if not original_trail_id:
        raise ValueError(f"{source_label}: 빈 trail_id가 있습니다.")

    actual_source = str(properties.get("source", "")).strip().upper()
    if actual_source != source_label:
        raise ValueError(
            f"{source_label}: source 값이 예상과 다릅니다. expected={source_label}, actual={actual_source}"
        )

    upper_trail_id = original_trail_id.upper()
    source_prefix = f"{source_label}_"

    if upper_trail_id.startswith(source_prefix):
        new_trail_id = original_trail_id
    else:
        new_trail_id = f"{source_label}_{original_trail_id}"

    raw_tags = properties.get("raw_tags")
    if raw_tags is None or not isinstance(raw_tags, dict):
        raw_tags = {}
    else:
        raw_tags = dict(raw_tags)

    raw_tags["original_trail_id"] = original_trail_id

    properties["trail_id"] = new_trail_id
    properties["source"] = source_label
    properties["raw_tags"] = raw_tags

    return {
        "type": "Feature",
        "properties": properties,
        "geometry": geometry,
    }


def geometry_hash(geometry: dict[str, Any]) -> str:
    raw = json.dumps(geometry, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def safe_length_key(length_value: Any) -> str:
    try:
        return f"{float(length_value):.1f}"
    except (TypeError, ValueError):
        return "INVALID"


def make_same_source_exact_key(feature: dict[str, Any]) -> str:
    payload = {
        "source": feature["properties"].get("source"),
        "source_ref": feature["properties"].get("source_ref"),
        "geometry_hash": geometry_hash(feature["geometry"]),
        "length_key": safe_length_key(feature["properties"].get("length_m")),
    }
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def to_metric_linestring(geometry: dict[str, Any], transformer: Transformer):
    geom = shape(geometry)
    if geom.geom_type != "LineString":
        raise ValueError(f"LineString만 지원합니다. actual={geom.geom_type}")
    return transform(transformer.transform, geom)


def bbox_to_grid_keys(bounds: tuple[float, float, float, float], grid_size_m: float) -> list[tuple[int, int]]:
    minx, miny, maxx, maxy = bounds
    start_x = math.floor(minx / grid_size_m)
    end_x = math.floor(maxx / grid_size_m)
    start_y = math.floor(miny / grid_size_m)
    end_y = math.floor(maxy / grid_size_m)

    keys: list[tuple[int, int]] = []
    for gx in range(start_x, end_x + 1):
        for gy in range(start_y, end_y + 1):
            keys.append((gx, gy))
    return keys


def expand_bounds(bounds: tuple[float, float, float, float], margin_m: float) -> tuple[float, float, float, float]:
    minx, miny, maxx, maxy = bounds
    return (
        minx - margin_m,
        miny - margin_m,
        maxx + margin_m,
        maxy + margin_m,
    )


def build_public_spatial_index(
        public_features: list[dict[str, Any]],
        transformer: Transformer,
        distance_tolerance_m: float,
        grid_size_m: float,
) -> tuple[list[Any], dict[tuple[int, int], list[int]]]:
    public_metric_geoms: list[Any] = []
    public_grid: dict[tuple[int, int], list[int]] = defaultdict(list)

    for index, feature in enumerate(public_features):
        metric_geom = to_metric_linestring(feature["geometry"], transformer)
        public_metric_geoms.append(metric_geom)

        expanded = expand_bounds(metric_geom.bounds, distance_tolerance_m)
        for key in bbox_to_grid_keys(expanded, grid_size_m):
            public_grid[key].append(index)

    return public_metric_geoms, public_grid


def get_public_candidate_ids(
        osm_metric_geom,
        public_grid: dict[tuple[int, int], list[int]],
        distance_tolerance_m: float,
        grid_size_m: float,
) -> list[int]:
    expanded = expand_bounds(osm_metric_geom.bounds, distance_tolerance_m)
    candidate_ids: set[int] = set()

    for key in bbox_to_grid_keys(expanded, grid_size_m):
        for public_id in public_grid.get(key, []):
            candidate_ids.add(public_id)

    return list(candidate_ids)


def compute_public_coverage_ratio(
        osm_metric_geom,
        candidate_ids: list[int],
        public_metric_geoms: list[Any],
        distance_tolerance_m: float,
) -> tuple[float, float]:
    if osm_metric_geom.length <= 0:
        return 0.0, float("inf")

    candidate_buffers = []
    nearest_distance = float("inf")

    for public_id in candidate_ids:
        public_geom = public_metric_geoms[public_id]
        distance = osm_metric_geom.distance(public_geom)

        if distance < nearest_distance:
            nearest_distance = distance

        if distance <= distance_tolerance_m:
            candidate_buffers.append(public_geom.buffer(distance_tolerance_m))

    if not candidate_buffers:
        return 0.0, nearest_distance

    public_buffer_union = unary_union(candidate_buffers)
    covered_length = osm_metric_geom.intersection(public_buffer_union).length
    coverage_ratio = covered_length / osm_metric_geom.length

    return coverage_ratio, nearest_distance


def normalize_optional_value(value: Any) -> Any:
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


def choose_preferred_value(
        existing_value: Any,
        incoming_value: Any,
        existing_source: str | None,
        incoming_source: str | None,
) -> Any:
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


def merge_raw_tags(
        existing_raw_tags: Any,
        incoming_raw_tags: Any,
        existing_source: str | None = None,
        incoming_source: str | None = None,
) -> dict[str, Any]:
    raw_tags1 = existing_raw_tags if isinstance(existing_raw_tags, dict) else {}
    raw_tags2 = incoming_raw_tags if isinstance(incoming_raw_tags, dict) else {}

    existing_priority = get_source_priority(existing_source)
    incoming_priority = get_source_priority(incoming_source)

    if incoming_priority > existing_priority:
        return {**raw_tags1, **raw_tags2}
    return {**raw_tags2, **raw_tags1}


def find_best_public_match(
        osm_metric_geom,
        candidate_ids: list[int],
        public_metric_geoms: list[Any],
        distance_tolerance_m: float,
) -> int | None:
    best_public_id: int | None = None
    best_distance = float("inf")

    for public_id in candidate_ids:
        public_geom = public_metric_geoms[public_id]
        distance = osm_metric_geom.distance(public_geom)

        if distance <= distance_tolerance_m and distance < best_distance:
            best_distance = distance
            best_public_id = public_id

    return best_public_id


def enrich_public_with_osm(public_feature: dict[str, Any], osm_feature: dict[str, Any]) -> int:
    public_props = public_feature["properties"]
    osm_props = osm_feature["properties"]

    original_public_surface = normalize_optional_value(public_props.get("surface"))
    incoming_osm_surface = normalize_optional_value(osm_props.get("surface"))

    public_props["surface"] = choose_preferred_value(
        public_props.get("surface"),
        osm_props.get("surface"),
        public_props.get("source"),
        osm_props.get("source"),
    )
    public_props["trail_type"] = choose_preferred_value(
        public_props.get("trail_type"),
        osm_props.get("trail_type"),
        public_props.get("source"),
        osm_props.get("source"),
    )
    public_props["source_ref"] = choose_preferred_value(
        public_props.get("source_ref"),
        osm_props.get("source_ref"),
        public_props.get("source"),
        osm_props.get("source"),
    )
    public_props["raw_tags"] = merge_raw_tags(
        public_props.get("raw_tags"),
        osm_props.get("raw_tags"),
        public_props.get("source"),
        osm_props.get("source"),
    )

    raw_tags = public_props.get("raw_tags")
    if not isinstance(raw_tags, dict):
        raw_tags = {}
        public_props["raw_tags"] = raw_tags

    raw_tags["osm_enriched"] = True

    if incoming_osm_surface is not None:
        raw_tags["osm_surface"] = incoming_osm_surface

    osm_source_ref = normalize_optional_value(osm_props.get("source_ref"))
    if osm_source_ref is not None:
        raw_tags["osm_source_ref"] = osm_source_ref

    if original_public_surface is None and incoming_osm_surface is not None:
        return 1
    return 0


def build_summary(features: list[dict[str, Any]]) -> dict[str, Any]:
    source_counts: dict[str, int] = {}
    geometry_type_counts: dict[str, int] = {}
    trail_type_counts: dict[str, int] = {}
    length_le_zero = 0
    missing_required_feature_count = 0

    for feature in features:
        properties = feature["properties"]
        geometry = feature["geometry"]

        source = str(properties.get("source"))
        source_counts[source] = source_counts.get(source, 0) + 1

        geometry_type = str(geometry.get("type"))
        geometry_type_counts[geometry_type] = geometry_type_counts.get(geometry_type, 0) + 1

        trail_type = str(properties.get("trail_type"))
        trail_type_counts[trail_type] = trail_type_counts.get(trail_type, 0) + 1

        try:
            length_m = float(properties.get("length_m", 0))
            if length_m <= 0:
                length_le_zero += 1
        except (TypeError, ValueError):
            length_le_zero += 1

        for key in REQUIRED_PROPERTIES:
            if key not in properties:
                missing_required_feature_count += 1
                break

    return {
        "total_features": len(features),
        "source_counts": source_counts,
        "geometry_type_counts": geometry_type_counts,
        "trail_type_counts": trail_type_counts,
        "length_le_zero": length_le_zero,
        "missing_required_feature_count": missing_required_feature_count,
    }


def average_or_none(values: list[float]) -> float | None:
    if not values:
        return None
    return round(sum(values) / len(values), 3)


def median_or_none(values: list[float]) -> float | None:
    if not values:
        return None
    return round(statistics.median(values), 3)


def bucket_coverage_ratio(value: float) -> str:
    if value < 0.2:
        return "0.0~0.2"
    if value < 0.4:
        return "0.2~0.4"
    if value < 0.6:
        return "0.4~0.6"
    if value < 0.8:
        return "0.6~0.8"
    return "0.8~1.0"


def bucket_distance(value: float) -> str:
    if math.isinf(value):
        return "INF"
    if value <= 10:
        return "0~10m"
    if value <= 20:
        return "10~20m"
    if value <= 35:
        return "20~35m"
    return "35m 초과"


def build_quality_summary(
        skipped_lengths: list[float],
        kept_lengths: list[float],
        skipped_coverages: list[float],
        kept_coverages: list[float],
        skipped_distances: list[float],
        kept_distances: list[float],
        osm_input_count: int,
        osm_skipped_count: int,
        osm_kept_count: int,
) -> dict[str, Any]:
    quality = {
        "osm_skip_rate": round(osm_skipped_count / osm_input_count, 4) if osm_input_count else 0.0,
        "osm_keep_rate": round(osm_kept_count / osm_input_count, 4) if osm_input_count else 0.0,
        "skipped_osm_avg_length_m": average_or_none(skipped_lengths),
        "skipped_osm_median_length_m": median_or_none(skipped_lengths),
        "kept_osm_avg_length_m": average_or_none(kept_lengths),
        "kept_osm_median_length_m": median_or_none(kept_lengths),
        "skipped_osm_avg_coverage_ratio": average_or_none(skipped_coverages),
        "skipped_osm_median_coverage_ratio": median_or_none(skipped_coverages),
        "kept_osm_avg_coverage_ratio": average_or_none(kept_coverages),
        "kept_osm_median_coverage_ratio": median_or_none(kept_coverages),
        "skipped_osm_avg_nearest_distance_m": average_or_none(skipped_distances),
        "skipped_osm_median_nearest_distance_m": median_or_none(skipped_distances),
        "kept_osm_avg_nearest_distance_m": average_or_none(kept_distances),
        "kept_osm_median_nearest_distance_m": median_or_none(kept_distances),
        "skipped_coverage_bucket_counts": dict(Counter(bucket_coverage_ratio(v) for v in skipped_coverages)),
        "kept_coverage_bucket_counts": dict(Counter(bucket_coverage_ratio(v) for v in kept_coverages)),
        "skipped_distance_bucket_counts": dict(Counter(bucket_distance(v) for v in skipped_distances)),
        "kept_distance_bucket_counts": dict(Counter(bucket_distance(v) for v in kept_distances)),
    }
    return quality


def merge_public_first(
        public_features: list[dict[str, Any]],
        osm_features: list[dict[str, Any]],
        distance_tolerance_m: float,
        coverage_threshold: float,
        grid_size_m: float,
) -> tuple[list[dict[str, Any]], dict[str, Any], dict[str, Any]]:
    merged_features: list[dict[str, Any]] = []
    public_output_index_map: dict[int, int] = {}

    seen_same_source_exact_keys: set[str] = set()

    transformer = Transformer.from_crs("EPSG:4326", "EPSG:5179", always_xy=True)

    public_metric_geoms, public_grid = build_public_spatial_index(
        public_features=public_features,
        transformer=transformer,
        distance_tolerance_m=distance_tolerance_m,
        grid_size_m=grid_size_m,
    )

    stats: dict[str, Any] = {
        "public_input_count": len(public_features),
        "osm_input_count": len(osm_features),
        "public_same_source_duplicates_removed": 0,
        "osm_same_source_duplicates_removed": 0,
        "osm_skipped_by_public_priority": 0,
        "osm_kept_as_fallback": 0,
        "osm_skipped_with_surface": 0,
        "public_enriched_surface_count": 0,
        "merged_output_count": 0,
        "distance_tolerance_m": distance_tolerance_m,
        "coverage_threshold": coverage_threshold,
        "grid_size_m": grid_size_m,
        "avg_public_candidates_per_osm": 0.0,
    }

    total_candidate_count = 0

    skipped_lengths: list[float] = []
    kept_lengths: list[float] = []
    skipped_coverages: list[float] = []
    kept_coverages: list[float] = []
    skipped_distances: list[float] = []
    kept_distances: list[float] = []

    for public_index, feature in enumerate(public_features):
        normalized = normalize_feature(feature, "PUBLIC")
        same_source_key = make_same_source_exact_key(normalized)

        if same_source_key in seen_same_source_exact_keys:
            stats["public_same_source_duplicates_removed"] += 1
            continue

        seen_same_source_exact_keys.add(same_source_key)
        public_output_index_map[public_index] = len(merged_features)
        merged_features.append(normalized)

    for feature in osm_features:
        normalized = normalize_feature(feature, "OSM")
        same_source_key = make_same_source_exact_key(normalized)

        if same_source_key in seen_same_source_exact_keys:
            stats["osm_same_source_duplicates_removed"] += 1
            continue

        osm_metric_geom = to_metric_linestring(normalized["geometry"], transformer)
        candidate_ids = get_public_candidate_ids(
            osm_metric_geom=osm_metric_geom,
            public_grid=public_grid,
            distance_tolerance_m=distance_tolerance_m,
            grid_size_m=grid_size_m,
        )
        total_candidate_count += len(candidate_ids)

        coverage_ratio, nearest_distance = compute_public_coverage_ratio(
            osm_metric_geom=osm_metric_geom,
            candidate_ids=candidate_ids,
            public_metric_geoms=public_metric_geoms,
            distance_tolerance_m=distance_tolerance_m,
        )

        raw_tags = normalized["properties"].get("raw_tags")
        if not isinstance(raw_tags, dict):
            raw_tags = {}
            normalized["properties"]["raw_tags"] = raw_tags

        raw_tags["merge_public_coverage_ratio"] = round(coverage_ratio, 4)
        raw_tags["merge_public_nearest_distance_m"] = (
            None if math.isinf(nearest_distance) else round(nearest_distance, 2)
        )

        length_m = float(normalized["properties"]["length_m"])

        if coverage_ratio >= coverage_threshold:
            best_public_id = find_best_public_match(
                osm_metric_geom=osm_metric_geom,
                candidate_ids=candidate_ids,
                public_metric_geoms=public_metric_geoms,
                distance_tolerance_m=distance_tolerance_m,
            )

            if best_public_id is not None and best_public_id in public_output_index_map:
                public_output_index = public_output_index_map[best_public_id]
                enriched_surface_count = enrich_public_with_osm(
                    merged_features[public_output_index],
                    normalized,
                )
                stats["public_enriched_surface_count"] += enriched_surface_count

            osm_surface = normalize_optional_value(normalized["properties"].get("surface"))
            if osm_surface is not None:
                stats["osm_skipped_with_surface"] += 1

            stats["osm_skipped_by_public_priority"] += 1
            skipped_lengths.append(length_m)
            skipped_coverages.append(coverage_ratio)
            if not math.isinf(nearest_distance):
                skipped_distances.append(nearest_distance)
            continue

        seen_same_source_exact_keys.add(same_source_key)
        stats["osm_kept_as_fallback"] += 1
        kept_lengths.append(length_m)
        kept_coverages.append(coverage_ratio)
        if not math.isinf(nearest_distance):
            kept_distances.append(nearest_distance)
        merged_features.append(normalized)

    if osm_features:
        stats["avg_public_candidates_per_osm"] = round(total_candidate_count / len(osm_features), 2)

    stats["merged_output_count"] = len(merged_features)

    quality_summary = build_quality_summary(
        skipped_lengths=skipped_lengths,
        kept_lengths=kept_lengths,
        skipped_coverages=skipped_coverages,
        kept_coverages=kept_coverages,
        skipped_distances=skipped_distances,
        kept_distances=kept_distances,
        osm_input_count=len(osm_features),
        osm_skipped_count=stats["osm_skipped_by_public_priority"],
        osm_kept_count=stats["osm_kept_as_fallback"],
    )

    return merged_features, stats, quality_summary


def main() -> None:
    if not OSM_INPUT_PATH.exists():
        raise FileNotFoundError(f"OSM 입력 파일이 없습니다: {OSM_INPUT_PATH}")

    if not PUBLIC_INPUT_PATH.exists():
        raise FileNotFoundError(f"PUBLIC 입력 파일이 없습니다: {PUBLIC_INPUT_PATH}")

    public_data = read_json(PUBLIC_INPUT_PATH)
    public_features = validate_feature_collection(public_data, "PUBLIC")

    osm_data = read_json(OSM_INPUT_PATH)
    osm_features = validate_feature_collection(osm_data, "OSM")

    merged_features, merge_stats, quality_summary = merge_public_first(
        public_features=public_features,
        osm_features=osm_features,
        distance_tolerance_m=DISTANCE_TOLERANCE_M,
        coverage_threshold=COVERAGE_THRESHOLD,
        grid_size_m=GRID_SIZE_M,
    )

    result = {
        "type": "FeatureCollection",
        "features": merged_features,
    }
    write_json(OUTPUT_PATH, result)

    summary = build_summary(merged_features)

    print("----- PUBLIC 우선 병합 완료 -----")
    print(f"PUBLIC 입력 파일: {PUBLIC_INPUT_PATH}")
    print(f"OSM 입력 파일: {OSM_INPUT_PATH}")
    print(f"출력 파일: {OUTPUT_PATH}")
    print()

    print("----- 병합 통계 -----")
    for key, value in merge_stats.items():
        print(f"{key}: {value}")
    print()

    print("----- 병합 품질 요약 -----")
    for key, value in quality_summary.items():
        print(f"{key}: {value}")
    print()

    print("----- 내용 점검 요약 -----")
    for key, value in summary.items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()