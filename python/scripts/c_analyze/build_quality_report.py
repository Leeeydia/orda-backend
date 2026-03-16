from __future__ import annotations

from collections import Counter
from pathlib import Path
from typing import Any

from config import FINAL_TRAIL_DATASET_PATH, QUALITY_REPORT_PATH, SUMMIT_POINTS_PATH
from pipeline import read_geojson_features


def save_markdown(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def collect_duplicate_edge_ids(final_features: list[dict[str, Any]]) -> list[str]:
    seen: set[str] = set()
    duplicates: list[str] = []

    for feature in final_features:
        properties = feature.get("properties", {})
        edge_id = properties.get("edge_id")

        if not isinstance(edge_id, str):
            continue

        if edge_id in seen:
            duplicates.append(edge_id)
        else:
            seen.add(edge_id)

    return duplicates


def build_qa_summary(final_features: list[dict[str, Any]]) -> dict[str, int]:
    qa_values = [
        feature.get("properties", {}).get("qa_status")
        for feature in final_features
    ]
    counter = Counter(qa_values)

    return {
        "pass": counter.get("pass", 0),
        "fail": counter.get("fail", 0),
    }


def build_summit_source_text(summit_features: list[dict[str, Any]]) -> str:
    sources = {
        feature.get("properties", {}).get("source")
        for feature in summit_features
        if isinstance(feature.get("properties", {}).get("source"), str)
    }

    if not sources:
        return "unknown"

    return ", ".join(sorted(sources))


def build_final_sample_lines(final_features: list[dict[str, Any]]) -> list[str]:
    lines: list[str] = []

    for feature in final_features[:3]:
        properties = feature.get("properties", {})
        lines.append(
            f"- {properties.get('edge_id')} | "
            f"slope_percent: {properties.get('slope_percent')} | "
            f"difficulty: {properties.get('difficulty')} | "
            f"nearest_summit_id: {properties.get('nearest_summit_id')} | "
            f"qa_status: {properties.get('qa_status')}"
        )

    return lines


def build_summit_sample_lines(summit_features: list[dict[str, Any]]) -> list[str]:
    lines: list[str] = []

    for feature in summit_features[:3]:
        properties = feature.get("properties", {})
        lines.append(
            f"- {properties.get('summit_id')} | "
            f"name: {properties.get('name')} | "
            f"elevation_m: {properties.get('elevation_m')} | "
            f"source: {properties.get('source')} | "
            f"radius_m: {properties.get('radius_m')}"
        )

    return lines


def build_quality_report_markdown(
        final_features: list[dict[str, Any]],
        summit_features: list[dict[str, Any]],
) -> str:
    qa_summary = build_qa_summary(final_features)
    duplicate_edge_ids = collect_duplicate_edge_ids(final_features)
    summit_source_text = build_summit_source_text(summit_features)

    duplicate_edge_text = ", ".join(duplicate_edge_ids) if duplicate_edge_ids else "없음"

    final_sample_lines = build_final_sample_lines(final_features)
    summit_sample_lines = build_summit_sample_lines(summit_features)

    lines = [
        "# C Stage Quality Report",
        "",
        "## 1. Input Summary",
        f"- final_trail_dataset features: {len(final_features)}",
        f"- summit_points features: {len(summit_features)}",
        "- DEM applied: yes",
        "",
        "## 2. Output Summary",
        f"- final_trail_dataset.geojson features: {len(final_features)}",
        f"- summit_points.geojson features: {len(summit_features)}",
        f"- quality_report.md generated: yes",
        "",
        "## 3. QA Summary",
        f"- qa pass: {qa_summary['pass']}",
        f"- qa fail: {qa_summary['fail']}",
        f"- duplicate edge ids: {duplicate_edge_text}",
        "",
        "## 4. Data Status",
        f"- summit source: {summit_source_text}",
        "- note: summit actual data not applied yet",
        "",
        "## 5. Final Trail Sample",
        *final_sample_lines,
        "",
        "## 6. Summit Sample",
        *summit_sample_lines,
        "",
        "## 7. Notes",
        "- This report is generated from interim C-stage outputs.",
        "- Replace summit input with actual OSM summit data and rerun before final delivery.",
    ]

    return "\n".join(lines)


def main() -> None:
    final_features = read_geojson_features(FINAL_TRAIL_DATASET_PATH)
    summit_features = read_geojson_features(SUMMIT_POINTS_PATH)

    markdown = build_quality_report_markdown(
        final_features=final_features,
        summit_features=summit_features,
    )
    save_markdown(QUALITY_REPORT_PATH, markdown)

    print("[QUALITY_REPORT_BUILD_RESULT]")
    print(f"  output_path: {QUALITY_REPORT_PATH}")
    print(f"  final_feature_count: {len(final_features)}")
    print(f"  summit_feature_count: {len(summit_features)}")


if __name__ == "__main__":
    main()