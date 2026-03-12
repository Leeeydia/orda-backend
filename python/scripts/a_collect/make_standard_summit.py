import json
from pathlib import Path


INPUT_PATH = Path("data/interim/a_output/gyeryong_osm_peak_filtered.geojson")
OUTPUT_PATH = Path("data/interim/a_output/standard_summit.geojson")


def to_number(value):
    if value is None:
        return None

    text = str(value).strip()
    if text == "":
        return None

    try:
        number = float(text)
        if number.is_integer():
            return int(number)
        return number
    except ValueError:
        return None


def normalize_null(value):
    if value is None:
        return None

    if isinstance(value, str):
        value = value.strip()
        if value == "":
            return None

    return value


def build_source_ref(properties):
    source_ref = properties.get("id") or properties.get("@id")
    source_ref = normalize_null(source_ref)

    if source_ref is None:
        raise ValueError("source_ref로 사용할 OSM id(id 또는 @id)가 없습니다.")

    return source_ref


def build_name(properties):
    name = properties.get("name:ko") or properties.get("name")
    name = normalize_null(name)

    if name is None:
        raise ValueError("정상 name이 없습니다.")

    return name


def validate_point_geometry(feature, index):
    geometry = feature.get("geometry")
    if not geometry:
        raise ValueError(f"{index}번 feature: geometry가 없습니다.")

    geometry_type = geometry.get("type")
    if geometry_type != "Point":
        raise ValueError(f"{index}번 feature: geometry.type이 Point가 아닙니다. 현재값={geometry_type}")

    coordinates = geometry.get("coordinates")
    if not isinstance(coordinates, list) or len(coordinates) != 2:
        raise ValueError(f"{index}번 feature: Point coordinates 형식이 잘못되었습니다.")

    lng, lat = coordinates

    if not isinstance(lng, (int, float)) or not isinstance(lat, (int, float)):
        raise ValueError(f"{index}번 feature: coordinates가 숫자가 아닙니다.")

    if not (-180 <= lng <= 180 and -90 <= lat <= 90):
        raise ValueError(f"{index}번 feature: 좌표 범위가 비정상입니다. coordinates={coordinates}")

    return geometry


def main():
    if not INPUT_PATH.exists():
        raise FileNotFoundError(f"입력 파일이 없습니다: {INPUT_PATH}")

    with INPUT_PATH.open("r", encoding="utf-8") as f:
        data = json.load(f)

    if data.get("type") != "FeatureCollection":
        raise ValueError("입력 파일이 FeatureCollection이 아닙니다.")

    features = data.get("features", [])
    if not features:
        raise ValueError("입력 파일에 features가 없습니다.")

    sortable_items = []
    for index, feature in enumerate(features, start=1):
        properties = feature.get("properties", {})
        source_ref = build_source_ref(properties)
        sortable_items.append((source_ref, index, feature))

    sortable_items.sort(key=lambda x: x[0])

    output_features = []
    used_source_refs = set()
    used_summit_ids = set()

    for order, (source_ref, original_index, feature) in enumerate(sortable_items, start=1):
        properties = feature.get("properties", {})
        geometry = validate_point_geometry(feature, original_index)

        if source_ref in used_source_refs:
            raise ValueError(f"중복 source_ref 발견: {source_ref}")
        used_source_refs.add(source_ref)

        summit_id = f"S{order:04d}"
        if summit_id in used_summit_ids:
            raise ValueError(f"중복 summit_id 발견: {summit_id}")
        used_summit_ids.add(summit_id)

        name = build_name(properties)
        elevation_m = to_number(properties.get("ele"))

        standard_properties = {
            "summit_id": summit_id,
            "name": name,
            "elevation_m": elevation_m,
            "source": "OSM",
            "source_ref": source_ref,
            "mountain_name": None,
            "admin_region": None,
            "raw_tags": properties if properties else None,
        }

        output_features.append(
            {
                "type": "Feature",
                "properties": standard_properties,
                "geometry": geometry,
            }
        )

    output_data = {
        "type": "FeatureCollection",
        "features": output_features,
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)

    print("standard_summit.geojson 생성 완료")
    print(f"입력 feature 수: {len(features)}")
    print(f"출력 feature 수: {len(output_features)}")
    print(f"출력 경로: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()