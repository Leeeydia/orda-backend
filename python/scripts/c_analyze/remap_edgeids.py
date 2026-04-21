import json
import os
import io
import zipfile
import psycopg2
from pyproj import Transformer
import math

PMNTN_FOLDER = r"C:\Users\User\IdeaProjects\ORDA\backend\python\data\raw\forest\selected_pmntn_json"
MOUNTAIN_ZIP = r"C:\Users\User\IdeaProjects\ORDA\backend\python\data\raw\forest\mountain.zip"
TOP100_PATH = r"C:\Users\User\IdeaProjects\ORDA\backend\src\main\resources\data\top100mountains.json"
OUTPUT_PATH = r"C:\Users\User\IdeaProjects\ORDA\backend\src\main\resources\data\top100mountains_remapped.json"

transformer = Transformer.from_crs("EPSG:5186", "EPSG:4326", always_xy=True)

conn = psycopg2.connect(host="localhost", port=5432, dbname="orda", user="postgres", password="0000")
cur = conn.cursor()

with open(TOP100_PATH, encoding="utf-8") as f:
    mountains = json.load(f)

mountain_map = {m["name"]: m for m in mountains}

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((math.radians(lat2-lat1))/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin((math.radians(lon2-lon1))/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

def process_feature(feat, name_to_lines):
    mntn_nm = feat["attributes"].get("MNTN_NM", "")
    if mntn_nm not in mountain_map:
        return
    paths = feat.get("geometry", {}).get("paths", [])
    if not paths:
        return
    all_coords = [transformer.transform(x, y) for path in paths for x, y in path]
    avg_lon = sum(c[0] for c in all_coords) / len(all_coords)
    avg_lat = sum(c[1] for c in all_coords) / len(all_coords)
    m = mountain_map[mntn_nm]
    dist = haversine(float(m["latitude"]), float(m["longitude"]), avg_lat, avg_lon)
    if dist > 30000:
        return
    for path in paths:
        wgs84_path = [transformer.transform(x, y) for x, y in path]
        name_to_lines.setdefault(mntn_nm, []).append(wgs84_path)

print("selected_pmntn_json 폴더 읽는 중...")
name_to_lines = {}

for fname in os.listdir(PMNTN_FOLDER):
    fpath = os.path.join(PMNTN_FOLDER, fname)
    with open(fpath, encoding="utf-8") as f:
        data = json.load(f)
    for feat in data.get("features", []):
        process_feature(feat, name_to_lines)

print(f"폴더 매칭 산 수: {len(name_to_lines)}")

# 폴더에서 못 찾은 산만 mountain.zip에서 추가 탐색
missing = [name for name in mountain_map if name not in name_to_lines]
print(f"mountain.zip에서 추가 탐색할 산: {missing}")

print("mountain.zip 읽는 중...")
with zipfile.ZipFile(MOUNTAIN_ZIP) as outer:
    for entry in outer.namelist():
        if not entry.endswith("_geojson.zip"):
            continue
        with zipfile.ZipFile(io.BytesIO(outer.read(entry))) as inner:
            for name in inner.namelist():
                if not name.endswith(".json"):
                    continue
                data = json.loads(inner.read(name))
                for feat in data.get("features", []):
                    mntn_nm = feat["attributes"].get("MNTN_NM", "")
                    if mntn_nm in missing:
                        process_feature(feat, name_to_lines)

print(f"최종 매칭 산 수: {len(name_to_lines)}")

def find_edge_ids(cur, lines, buffer_m=10):
    linestrings = []
    for path in lines:
        coords = ",".join(f"{lon} {lat}" for lon, lat in path)
        linestrings.append(f"LINESTRING({coords})")
    multi = "GEOMETRYCOLLECTION(" + ",".join(linestrings) + ")"
    cur.execute("""
                SELECT DISTINCT edge_id FROM trail_edges
                WHERE ST_DWithin(
                              geom::geography,
                              ST_GeomFromText(%s, 4326)::geography,
                              %s
                      )
                """, (multi, buffer_m))
    return [row[0] for row in cur.fetchall()]

result = []
for m in mountains:
    name = m["name"]
    lines = name_to_lines.get(name, [])
    if lines:
        edge_ids = find_edge_ids(cur, lines)
        print(f"[O] {name}: PMNTN 등산로 {len(lines)}개 → edge {len(edge_ids)}개")
    else:
        edge_ids = []
        print(f"[X] {name}: PMNTN 데이터 없음")
    result.append({**m, "edgeIds": edge_ids})

with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print("\n완료: top100mountains_remapped.json 생성됨")
cur.close()
conn.close()