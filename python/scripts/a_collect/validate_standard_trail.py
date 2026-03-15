import argparse
import json
from collections import Counter
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
INPUT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "standard_trail.geojson"

REQUIRED_PROPERTIES = ["trail_id", "source", "length_m"]


def get_project_root() -> Path:
    """
    현재 파일 위치 기준으로 backend/python 루트 경로 계산
    현재 파일:
    backend/python/scripts/a_collect/validate_standard_trail.py
    """
    return Path(__file__).resolve().parents[2]


def is_null_like(value):
    return value is None


def main():
    input_path = INPUT_PATH.resolve()

    if not input_path.exists():
        raise FileNotFoundError(f"검수 대상 파일이 없습니다: {input_path}")

    print("[1/6] 파일 읽기 시작")
    print(f"검수 대상: {input_path}")

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    print("[2/6] 최상위 구조 검수")
    top_level_type_ok = data.get("type") == "FeatureCollection"
    features = data.get("features")

    if not isinstance(features, list):
        print("실패: features가 list가 아닙니다.")
        return

    total_features = len(features)

    print("[3/6] feature 단위 검사 시작")

    geometry_type_counter = Counter()
    trail_id_counter = Counter()

    missing_required_counts = Counter()
    empty_string_counts = Counter()
    null_counts = Counter()

    invalid_coordinate_order_count = 0
    invalid_geometry_count = 0
    invalid_length_count = 0
    missing_properties_count = 0

    nullable_fields = [
        "name",
        "surface",
        "mountain_name",
        "admin_region",
        "is_official",
        "raw_tags",
    ]

    for feature in features:
        properties = feature.get("properties")
        geometry = feature.get("geometry")

        if not isinstance(properties, dict):
            missing_properties_count += 1
            continue

        if not isinstance(geometry, dict):
            invalid_geometry_count += 1
            continue

        geometry_type = geometry.get("type")
        geometry_type_counter[geometry_type] += 1

        if geometry_type != "LineString":
            invalid_geometry_count += 1

        coordinates = geometry.get("coordinates")
        if not isinstance(coordinates, list) or len(coordinates) < 2:
            invalid_geometry_count += 1
        else:
            # 좌표 순서가 [lng, lat]처럼 보이는지 아주 기본적인 범위 검사
            # 한국 기준으로 lng는 대략 124~132, lat는 33~39 근처가 정상 범위
            first_coord = coordinates[0]

            if (
                    not isinstance(first_coord, (list, tuple))
                    or len(first_coord) < 2
            ):
                invalid_coordinate_order_count += 1
            else:
                lng = first_coord[0]
                lat = first_coord[1]

                if not isinstance(lng, (int, float)) or not isinstance(lat, (int, float)):
                    invalid_coordinate_order_count += 1
                else:
                    if not (120 <= lng <= 140 and 30 <= lat <= 45):
                        invalid_coordinate_order_count += 1

        trail_id = properties.get("trail_id")
        if trail_id is not None:
            trail_id_counter[trail_id] += 1
        else:
            missing_required_counts["trail_id"] += 1

        for field in REQUIRED_PROPERTIES:
            value = properties.get(field)
            if value is None:
                missing_required_counts[field] += 1
            elif isinstance(value, str) and value.strip() == "":
                empty_string_counts[field] += 1

        for nullable_field in nullable_fields:
            if nullable_field in properties and is_null_like(properties.get(nullable_field)):
                null_counts[nullable_field] += 1
            elif nullable_field in properties:
                value = properties.get(nullable_field)
                if isinstance(value, str) and value.strip() == "":
                    empty_string_counts[nullable_field] += 1

        length_m = properties.get("length_m")
        if not isinstance(length_m, (int, float)) or length_m <= 0:
            invalid_length_count += 1

    duplicate_trail_ids = {k: v for k, v in trail_id_counter.items() if v > 1}
    duplicate_trail_id_count = len(duplicate_trail_ids)

    print("[4/6] 결과 요약 출력")
    print()
    print("===== standard_trail.geojson 레벨 1 검수 결과 =====")
    print(f"FeatureCollection 여부: {top_level_type_ok}")
    print(f"총 feature 수: {total_features}")
    print(f"geometry 타입 분포: {dict(geometry_type_counter)}")
    print(f"LineString 이외 geometry 수: {invalid_geometry_count}")
    print(f"좌표 순서/범위 의심 건수: {invalid_coordinate_order_count}")
    print(f"trail_id 중복 개수: {duplicate_trail_id_count}")
    print(f"필수 properties 누락 feature 수: {missing_properties_count}")
    print(f"필수 필드 누락 개수: {dict(missing_required_counts)}")
    print(f"빈 문자열 발견 개수: {dict(empty_string_counts)}")
    print(f"null 개수(허용 필드 참고용): {dict(null_counts)}")
    print(f"length_m 이상치 개수: {invalid_length_count}")

    print()
    print("[5/6] 판정")

    has_error = False

    if not top_level_type_ok:
        print("- 실패: 최상위 type이 FeatureCollection이 아님")
        has_error = True

    if geometry_type_counter.get("LineString", 0) != total_features:
        print("- 실패: 모든 geometry가 LineString이 아님")
        has_error = True

    if duplicate_trail_id_count > 0:
        print("- 실패: trail_id 중복이 있음")
        has_error = True

    if missing_properties_count > 0:
        print("- 실패: properties 자체가 없는 feature가 있음")
        has_error = True

    if sum(missing_required_counts.values()) > 0:
        print("- 실패: 필수 필드 누락이 있음")
        has_error = True

    if invalid_length_count > 0:
        print("- 실패: length_m 이상치가 있음")
        has_error = True

    if invalid_coordinate_order_count > 0:
        print("- 경고: 좌표 순서 또는 좌표 범위가 의심되는 데이터가 있음")

    if sum(empty_string_counts.values()) > 0:
        print("- 경고: null 대신 빈 문자열이 들어간 필드가 있음")

    if not has_error:
        print("- 통과: 레벨 1 최소 기준 충족")

    print()
    print("[6/6] 검수 종료")


if __name__ == "__main__":
    main()