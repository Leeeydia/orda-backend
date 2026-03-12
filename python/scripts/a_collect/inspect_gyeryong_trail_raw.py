import json
from pathlib import Path

raw_path = Path("data/raw/gyeryong/gyeryong_osm_raw.geojson")

with raw_path.open("r", encoding="utf-8") as f:
    data = json.load(f)

features = data["features"]

name_exists = 0
name_missing = 0
id_exists = 0
id_missing = 0
at_id_exists = 0
at_id_missing = 0
surface_exists = 0
surface_missing = 0

for feature in features:
    props = feature.get("properties", {})

    if props.get("name") not in (None, ""):
        name_exists += 1
    else:
        name_missing += 1

    if props.get("id") not in (None, ""):
        id_exists += 1
    else:
        id_missing += 1

    if props.get("@id") not in (None, ""):
        at_id_exists += 1
    else:
        at_id_missing += 1

    if props.get("surface") not in (None, ""):
        surface_exists += 1
    else:
        surface_missing += 1

print("전체 feature 수:", len(features))
print("name 있음:", name_exists)
print("name 없음:", name_missing)
print("id 있음:", id_exists)
print("id 없음:", id_missing)
print("@id 있음:", at_id_exists)
print("@id 없음:", at_id_missing)
print("surface 있음:", surface_exists)
print("surface 없음:", surface_missing)