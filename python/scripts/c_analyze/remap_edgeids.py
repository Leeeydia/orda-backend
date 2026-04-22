import json
import re
import unicodedata
import math

GEOJSON_PATH = r"C:\Users\User\IdeaProjects\ORDA\backend\python\data\interim\b_output\trail_network_edges.geojson"
TOP100_PATH = r"C:\Users\User\IdeaProjects\ORDA\backend\src\main\resources\data\top100mountains.json"
OUTPUT_PATH = r"C:\Users\User\IdeaProjects\ORDA\backend\src\main\resources\data\top100mountains_remapped.json"

MAX_DISTANCE_M = 30000


def nfc(s):
    return unicodedata.normalize("NFC", s)


def remove_parentheses(name):
    return re.sub(r"\([^)]*\)", "", name).strip()


def haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((math.radians(lat2 - lat1)) / 2) ** 2 + \
        math.cos(phi1) * math.cos(phi2) * math.sin((math.radians(lon2 - lon1)) / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


print("top100mountains.json 로딩...")
with open(TOP100_PATH, encoding="utf-8") as f:
    mountains = json.load(f)

base_name_index = {}
for m in mountains:
    base = remove_parentheses(m["name"])
    base_name_index.setdefault(base, []).append(
        (m["name"], float(m["latitude"]), float(m["longitude"]))
    )

print("trail_network_edges.geojson 로딩...")
with open(GEOJSON_PATH, encoding="utf-8") as f:
    geojson = json.load(f)

name_to_edge_ids = {}

for feat in geojson["features"]:
    props = feat["properties"]
    raw_name = props.get("mountain_name")
    if not raw_name:
        continue
    mountain_name = nfc(raw_name)
    base = remove_parentheses(mountain_name)
    edge_id = props.get("edge_id")
    if not edge_id:
        continue
    coords = feat["geometry"]["coordinates"]
    if feat["geometry"]["type"] != "LineString":
        continue
    avg_lon = sum(c[0] for c in coords) / len(coords)
    avg_lat = sum(c[1] for c in coords) / len(coords)

    candidates = base_name_index.get(base, [])
    if not candidates:
        continue

    best_name = None
    best_dist = float("inf")
    for canonical_name, lat, lon in candidates:
        dist = haversine(lat, lon, avg_lat, avg_lon)
        if dist < best_dist:
            best_dist = dist
            best_name = canonical_name

    if best_dist > MAX_DISTANCE_M:
        continue

    name_to_edge_ids.setdefault(best_name, set()).add(edge_id)

print(f"매칭된 산 수: {len(name_to_edge_ids)}")

result = []
for m in mountains:
    name = m["name"]
    edge_ids = sorted(name_to_edge_ids.get(name, set()))
    if edge_ids:
        print(f"[O] {name}: edge {len(edge_ids)}개")
    else:
        print(f"[X] {name}: edge 없음")
    result.append({**m, "edgeIds": edge_ids})

with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print("\n완료: top100mountains_remapped.json 생성됨")