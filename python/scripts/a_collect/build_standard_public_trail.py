from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

from pyproj import Transformer
from shapely.geometry import LineString


BASE_DIR = Path(__file__).resolve().parents[2]

INPUT_DIR = BASE_DIR / "data" / "raw" / "forest" / "selected_pmntn_json"
OUTPUT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_public_trail.geojson"

SOURCE_NAME = "PUBLIC"
TRAIL_TYPE = "trail"

# 공공데이터 좌표계: GRS80 TM 중부원점
# EPSG:5186 → EPSG:4326 변환
TRANSFORMER = Transformer.from_crs("EPSG:5186", "EPSG:4326", always_xy=True)


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if text == "" or text == " ":
        return None
    return text


def to_bool_official() -> bool:
    return True


def build_name(mountain_name: str | None, section_name: str | None) -> str | None:
    if mountain_name and section_name:
        return f"{mountain_name} - {section_name}"
    return mountain_name or section_name


def transform_point(x: float, y: float) -> list[float]:
    lon, lat = TRANSFORMER.transform(x, y)
    return [round(lon, 7), round(lat, 7)]


def iter_paths(feature: dict[str, Any]) -> Iterable[list[list[float]]]:
    geometry = feature.get("geometry", {})
    paths = geometry.get("paths", [])
    if not isinstance(paths, list):
        return []
    return paths


def dedupe_consecutive_coords(coords: list[list[float]]) -> list[list[float]]:
    if not coords:
        return []

    cleaned = [coords[0]]
    for coord in coords[1:]:
        if coord != cleaned[-1]:
            cleaned.append(coord)
    return cleaned


def build_linestring_coords(feature: dict[str, Any]) -> list[list[float]] | None:
    merged_coords: list[list[float]] = []

    for path in iter_paths(feature):
        if not isinstance(path, list):
            continue

        transformed_path: list[list[float]] = []
        for point in path:
            if not isinstance(point, list) or len(point) < 2:
                continue

            x, y = point[0], point[1]
            transformed_path.append(transform_point(x, y))

        transformed_path = dedupe_consecutive_coords(transformed_path)

        if len(transformed_path) < 2:
            continue

        if not merged_coords:
            merged_coords.extend(transformed_path)
        else:
            # path 경계에서 같은 점이 이어지면 중복 제거
            if merged_coords[-1] == transformed_path[0]:
                merged_coords.extend(transformed_path[1:])
            else:
                merged_coords.extend(transformed_path)

    merged_coords = dedupe_consecutive_coords(merged_coords)

    if len(merged_coords) < 2:
        return None

    # 전체가 같은 점만 반복된 경우 방어
    if len({tuple(coord) for coord in merged_coords}) < 2:
        return None

    return merged_coords


def compute_length_m(coords_wgs84: list[list[float]]) -> float:
    line = LineString(coords_wgs84)
    # 위경도 그대로 길이 재면 안 되므로,
    # 경위도를 다시 Web Mercator로 보내 길이 계산
    metric_transformer = Transformer.from_crs("EPSG:4326", "EPSG:3857", always_xy=True)
    projected_coords = [metric_transformer.transform(x, y) for x, y in coords_wgs84]
    projected_line = LineString(projected_coords)
    return round(float(projected_line.length), 3)


def build_feature(raw_feature: dict[str, Any], seq: int) -> dict[str, Any] | None:
    attrs = raw_feature.get("attributes", {})

    coords = build_linestring_coords(raw_feature)
    if coords is None:
        return None

    length_m = compute_length_m(coords)
    if length_m <= 0:
        return None

    mountain_name = clean_text(attrs.get("MNTN_NM"))
    section_name = clean_text(attrs.get("PMNTN_NM"))
    difficulty = clean_text(attrs.get("PMNTN_DFFL"))
    risk_note = clean_text(attrs.get("PMNTN_RISK"))
    data_standard_date = clean_text(attrs.get("DATA_STDR_"))

    properties = {
        "trail_id": f"PUBLIC_TRAIL_{seq:06d}",
        "name": build_name(mountain_name, section_name),
        "source": SOURCE_NAME,
        "source_ref": f"PMNTN_SN:{attrs.get('PMNTN_SN')}",
        "length_m": length_m,
        "surface": clean_text(attrs.get("PMNTN_MTRQ")),
        "trail_type": TRAIL_TYPE,
        "mountain_name": mountain_name,
        "admin_region": None,
        "is_official": to_bool_official(),
        "difficulty": difficulty,
        "section_name": section_name,
        "risk_note": risk_note,
        "data_standard_date": data_standard_date,
        "raw_tags": {
            "PMNTN_SN": attrs.get("PMNTN_SN"),
            "MNTN_CODE": attrs.get("MNTN_CODE"),
            "PMNTN_MAIN": clean_text(attrs.get("PMNTN_MAIN")),
            "PMNTN_DFFL": difficulty,
            "PMNTN_UPPL": attrs.get("PMNTN_UPPL"),
            "PMNTN_GODN": attrs.get("PMNTN_GODN"),
            "PMNTN_MTRQ": clean_text(attrs.get("PMNTN_MTRQ")),
            "PMNTN_CNRL": clean_text(attrs.get("PMNTN_CNRL")),
            "PMNTN_CLS_": clean_text(attrs.get("PMNTN_CLS_")),
            "PMNTN_RISK": risk_note,
            "PMNTN_RECO": clean_text(attrs.get("PMNTN_RECO")),
            "DATA_STDR_": data_standard_date,
            "MNTN_ID": clean_text(attrs.get("MNTN_ID")),
        },
    }

    return {
        "type": "Feature",
        "properties": properties,
        "geometry": {
            "type": "LineString",
            "coordinates": coords,
        },
    }


def main() -> None:
    if not INPUT_DIR.exists():
        raise FileNotFoundError(f"입력 폴더가 없습니다: {INPUT_DIR}")

    json_files = sorted(INPUT_DIR.glob("*.json"))
    if not json_files:
        raise FileNotFoundError(f"입력 json 파일이 없습니다: {INPUT_DIR}")

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    features: list[dict[str, Any]] = []
    seq = 1

    for json_file in json_files:
        data = read_json(json_file)
        raw_features = data.get("features", [])

        print(f"[읽는 중] {json_file.name} - feature {len(raw_features)}개")

        for raw_feature in raw_features:
            feature = build_feature(raw_feature, seq)
            if feature is None:
                continue
            features.append(feature)
            seq += 1

    collection = {
        "type": "FeatureCollection",
        "features": features,
    }

    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(collection, f, ensure_ascii=False, indent=2)

    print("----- 완료 -----")
    print(f"출력 파일: {OUTPUT_PATH}")
    print(f"총 feature 수: {len(features)}")


if __name__ == "__main__":
    main()