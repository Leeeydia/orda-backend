from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

from pyproj import CRS, Transformer


BASE_DIR = Path(__file__).resolve().parents[2]

INPUT_DIR = BASE_DIR / "data" / "raw" / "forest" / "selected_pmntn_json"
OUTPUT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_public_trail_sample.geojson"

SOURCE_CRS = CRS.from_wkt(
    'PROJCS["PCS_ITRF2000_TM",'
    'GEOGCS["GCS_ITRF_2000",'
    'DATUM["D_ITRF_2000",'
    'SPHEROID["GRS_1980",6378137.0,298.257222101]],'
    'PRIMEM["Greenwich",0.0],'
    'UNIT["Degree",0.0174532925199433]],'
    'PROJECTION["Transverse_Mercator"],'
    'PARAMETER["False_Easting",200000.0],'
    'PARAMETER["False_Northing",600000.0],'
    'PARAMETER["Central_Meridian",127.0],'
    'PARAMETER["Scale_Factor",1.0],'
    'PARAMETER["Latitude_Of_Origin",38.0],'
    'UNIT["Meter",1.0]]'
)
TARGET_CRS = CRS.from_epsg(4326)

TRANSFORMER = Transformer.from_crs(SOURCE_CRS, TARGET_CRS, always_xy=True)


def read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def find_first_json_file(directory: Path) -> Path:
    json_files = sorted(directory.glob("*.json"))
    if not json_files:
        raise FileNotFoundError(f"입력 폴더에 json 파일이 없습니다: {directory}")
    return json_files[0]


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if text == "" or text == " ":
        return None
    return text


def to_float(value: Any) -> float | None:
    try:
        if value is None or str(value).strip() == "":
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def transform_point(x: float, y: float) -> list[float]:
    lon, lat = TRANSFORMER.transform(x, y)
    return [round(lon, 7), round(lat, 7)]


def transform_path(path_coords: list[list[float]]) -> list[list[float]]:
    result: list[list[float]] = []

    for point in path_coords:
        if not isinstance(point, list) or len(point) < 2:
            continue

        x, y = point[0], point[1]
        if x is None or y is None:
            continue

        result.append(transform_point(float(x), float(y)))

    return result


def linestring_length_m(coords: list[list[float]]) -> float:
    if len(coords) < 2:
        return 0.0

    total = 0.0
    for i in range(1, len(coords)):
        x1, y1 = coords[i - 1]
        x2, y2 = coords[i]
        total += math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2)

    return round(total, 2)


def normalize_feature(feature: dict[str, Any], index: int) -> dict[str, Any] | None:
    attributes = feature.get("attributes", {})
    geometry = feature.get("geometry", {})
    paths = geometry.get("paths", [])

    if not isinstance(paths, list) or not paths:
        return None

    first_path = paths[0]
    if not isinstance(first_path, list) or len(first_path) < 2:
        return None

    transformed_coords = transform_path(first_path)
    if len(transformed_coords) < 2:
        return None

    pmntn_sn = attributes.get("PMNTN_SN")
    mntn_code = clean_text(attributes.get("MNTN_CODE"))
    mntn_name = clean_text(attributes.get("MNTN_NM"))
    section_name = clean_text(attributes.get("PMNTN_NM"))
    difficulty = clean_text(attributes.get("PMNTN_DFFL"))
    surface = clean_text(attributes.get("PMNTN_MTRQ"))
    risk_text = clean_text(attributes.get("PMNTN_RISK"))
    standard_date = clean_text(attributes.get("DATA_STDR_"))
    mntn_id = clean_text(attributes.get("MNTN_ID"))

    trail_id = f"PUBLIC_TRAIL_{index:06d}"
    source_ref = f"PMNTN_SN:{pmntn_sn}" if pmntn_sn is not None else f"ROW:{index}"

    display_name_parts = [part for part in [mntn_name, section_name] if part]
    name = " - ".join(display_name_parts) if display_name_parts else trail_id

    length_attr_km = to_float(attributes.get("PMNTN_LT"))
    length_m = round(length_attr_km * 1000, 2) if length_attr_km is not None else linestring_length_m(first_path)

    raw_tags = {
        "PMNTN_SN": pmntn_sn,
        "MNTN_CODE": mntn_code,
        "PMNTN_MAIN": clean_text(attributes.get("PMNTN_MAIN")),
        "PMNTN_DFFL": difficulty,
        "PMNTN_UPPL": attributes.get("PMNTN_UPPL"),
        "PMNTN_GODN": attributes.get("PMNTN_GODN"),
        "PMNTN_MTRQ": surface,
        "PMNTN_CNRL": clean_text(attributes.get("PMNTN_CNRL")),
        "PMNTN_CLS_": clean_text(attributes.get("PMNTN_CLS_")),
        "PMNTN_RISK": risk_text,
        "PMNTN_RECO": clean_text(attributes.get("PMNTN_RECO")),
        "DATA_STDR_": standard_date,
        "MNTN_ID": mntn_id,
    }

    properties = {
        "trail_id": trail_id,
        "name": name,
        "source": "PUBLIC",
        "source_ref": source_ref,
        "length_m": length_m,
        "surface": surface,
        "trail_type": "trail",
        "mountain_name": mntn_name,
        "admin_region": None,
        "is_official": True,
        "difficulty": difficulty,
        "section_name": section_name,
        "risk_note": risk_text,
        "data_standard_date": standard_date,
        "raw_tags": raw_tags,
    }

    return {
        "type": "Feature",
        "properties": properties,
        "geometry": {
            "type": "LineString",
            "coordinates": transformed_coords,
        },
    }


def build_feature_collection(source_data: dict[str, Any]) -> dict[str, Any]:
    features = source_data.get("features", [])

    normalized_features: list[dict[str, Any]] = []
    for index, feature in enumerate(features, start=1):
        normalized = normalize_feature(feature, index)
        if normalized is not None:
            normalized_features.append(normalized)

    return {
        "type": "FeatureCollection",
        "features": normalized_features,
    }


def main() -> None:
    if not INPUT_DIR.exists():
        raise FileNotFoundError(f"입력 폴더가 없습니다: {INPUT_DIR}")

    input_path = find_first_json_file(INPUT_DIR)
    source_data = read_json(input_path)
    result = build_feature_collection(source_data)
    write_json(OUTPUT_PATH, result)

    print("[완료]")
    print(f"입력 파일: {input_path}")
    print(f"출력 파일: {OUTPUT_PATH}")
    print(f"생성 feature 수: {len(result['features'])}")


if __name__ == "__main__":
    main()