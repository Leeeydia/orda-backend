# ORDA Python 데이터 파이프라인

한국 등산로 네트워크를 구축하는 3단계 지리공간 데이터 파이프라인입니다.
OSM·산림청 원본 데이터를 수집·정규화하고, DEM 고도 정보와 정상 연결 정보를 부착한 최종 데이터셋을 만듭니다.

---

## 목차

1. [환경 설정](#1-환경-설정)
2. [폴더 구조](#2-폴더-구조)
3. [파이프라인 전체 흐름](#3-파이프라인-전체-흐름)
4. [단계별 실행 방법](#4-단계별-실행-방법)
    - [Stage A: 데이터 수집 및 병합](#stage-a-데이터-수집-및-병합)
    - [Stage B: 네트워크 정규화](#stage-b-네트워크-정규화)
    - [Stage C: 분석 및 속성 부착](#stage-c-분석-및-속성-부착)
    - [(선택) DB 적재](#선택-db-적재)
5. [단계별 검증 방법](#5-단계별-검증-방법)
6. [주요 상수 및 설정](#6-주요-상수-및-설정)
7. [산출물 스펙](#7-산출물-스펙)
8. [100대 명산 edgeIds 재매핑 절차](#8-100대-명산-edgeids-재매핑-절차)
9. [트러블슈팅](#9-트러블슈팅)

---

## 1. 환경 설정

### 사전 요구사항

| 항목 | 버전 |
|------|------|
| Python | 3.10 이상 |
| PostgreSQL + PostGIS | DB 적재 시 필요 |

### 가상환경 생성 및 패키지 설치

```bash
# 가상환경 생성
python -m venv venv

# 활성화 (Linux/macOS)
source venv/bin/activate

# 활성화 (Windows)
venv\Scripts\activate

# 패키지 설치
pip install -r requirements.txt
```

### 필수 원본 데이터 준비

파이프라인 실행 전 아래 파일을 지정 경로에 배치해야 합니다.

| 파일 | 위치 | 설명 |
|------|------|------|
| `south-korea-latest.osm.pbf` | `data/raw/osm/` | OSM 한국 전체 데이터 |
| `mountain.zip` | `data/raw/forest/` | 산림청 등산로 원본 ZIP |
| `korea_dem.tif` | `data/raw/dem/nasadem/` | NASA DEM GeoTIFF |

### 환경 변수 (DB 적재 시)

```bash
export ORDA_DB_HOST=localhost
export ORDA_DB_PORT=5432
export ORDA_DB_NAME=orda
export ORDA_DB_USER=postgres
export ORDA_DB_PASSWORD=0000
```

---

## 2. 폴더 구조

```
backend/python/
├── data/
│   ├── raw/
│   │   ├── osm/                          # OSM 원본 PBF
│   │   ├── dem/nasadem/                  # DEM GeoTIFF
│   │   └── forest/
│   │       ├── mountain.zip              # 산림청 원본 ZIP
│   │       └── selected_pmntn_json/      # 추출된 등산로 JSON (A-0 실행 후 생성)
│   ├── interim/
│   │   ├── a_output/                     # Stage A 중간 산출물
│   │   ├── b_output/                     # Stage B 중간 산출물
│   │   └── c_output/                     # Stage C 중간 산출물
│   └── final/                            # 최종 데이터셋
├── scripts/
│   ├── common/                           # 공통 유틸리티
│   ├── a_collect/                        # Stage A 스크립트
│   ├── b_normalize/                      # Stage B 스크립트
│   └── c_analyze/                        # Stage C 스크립트
├── output/
│   ├── logs/                             # 실행 로그
│   ├── reports/                          # 검증 리포트
│   ├── previews/                         # 시각화 결과
│   └── temp/                             # 임시 파일
├── requirements.txt
└── README.md
```

---

## 3. 파이프라인 전체 흐름

```
[원본 데이터]
  OSM PBF                   산림청 ZIP                  NASA DEM
     │                          │                           │
     ▼                          ▼                           │
  extract_osm.py    extract_public_pmntn_from_outer_zip.py │
     │                          │                           │
     │              build_standard_public_trail.py          │
     │                          │                           │
     ▼                          ▼                           │
standard_osm_trail.geojson  standard_public_trail.geojson  │
standard_summit.geojson         │                           │
     │                          │                           │
     └──────────┬───────────────┘                           │
                ▼                                           │
         merge_standard_trails.py                           │
                │                                           │
                ▼                                           │
         standard_trail.geojson  ◄── [Stage A 완료]         │
                │                                           │
                ▼                                           │
         build_network.py                                   │
                │                                           │
                ▼                                           │
  trail_network_edges.geojson  ◄── [Stage B 완료]           │
  trail_network_nodes.geojson                               │
                │                                           │
                └──────────────────┬────────────────────────┘
                                   ▼
                             run_pipeline.py
                                   │
                                   ▼
                   node_with_elevation.geojson
                   final_trail_dataset.geojson  ◄── [Stage C 완료]
                   summit_points.geojson
                   quality_report.md
                                   │
                            (선택) load_to_postgis.py
                                   │
                                   ▼
                        PostgreSQL / PostGIS DB
```

---

## 4. 단계별 실행 방법

> 모든 명령은 `backend/python/` 디렉터리에서 실행합니다.

---

### Stage A: 데이터 수집 및 병합

**목적:** OSM과 산림청 등산로를 각각 표준 형식으로 변환한 후 병합합니다.

#### A-0. 산림청 ZIP 사전 추출 (최초 1회)

```bash
python scripts/a_collect/extract_public_pmntn_from_outer_zip.py
```

- 입력: `data/raw/forest/mountain.zip`
- 출력: `data/raw/forest/selected_pmntn_json/*.json`, `selected_pmntn_manifest.json`
- `mountain.zip` 안의 중첩 ZIP에서 `PMNTN_*.json` 파일을 추출합니다.

#### A-1. OSM 등산로·정상 추출

```bash
python scripts/a_collect/extract_osm.py
```

- 입력: `data/raw/osm/south-korea-latest.osm.pbf`
- 출력: `data/interim/a_output/standard_osm_trail.geojson`
- 출력: `data/interim/a_output/standard_summit.geojson`
- `sac_scale`, `surface`, 명칭 패턴 등으로 등산로를 필터링합니다.
- ID 형식: 등산로 `T0001`, 정상 `S0001`

#### A-2. 산림청 공식 등산로 변환

```bash
python scripts/a_collect/build_standard_public_trail.py
```

- 입력: `data/raw/forest/selected_pmntn_json/*.json`
- 출력: `data/interim/a_output/standard_public_trail.geojson`
- 좌표계: `PCS_ITRF2000_TM` → `WGS84 (EPSG:4326)` 변환
- ID 형식: `PUBLIC_TRAIL_000001`

#### A-3. 등산로 병합

```bash
python scripts/a_collect/merge_standard_trails.py
```

- 입력: `standard_osm_trail.geojson`, `standard_public_trail.geojson`
- 출력: `data/interim/a_output/standard_trail.geojson`
- 산림청 데이터 우선, OSM은 공식 데이터 커버리지 60% 미만인 구간만 보완합니다.
- 500m 격자 기반 공간 인덱스를 사용합니다.

---

### Stage B: 네트워크 정규화

**목적:** 등산로 LineString을 노드-엣지 그래프로 변환합니다.

```bash
python scripts/b_normalize/build_network.py
```

- 입력: `data/interim/a_output/standard_trail.geojson`
- 출력: `data/interim/b_output/trail_network_edges.geojson`
- 출력: `data/interim/b_output/trail_network_nodes.geojson`

**내부 처리 순서:**
1. 입력 정규화 (MultiLineString 분해, 유효성 검사)
2. 초기 그래프 생성 (엣지 중복 제거)
3. 교차점에서 엣지 분할
4. 차수-2 노드 병합 (동일 속성 조건 충족 시)
5. 단절·짧은 엣지 제거 (기본 임계값 20m)
6. 최종 피처 생성 및 ID 부여 (노드 `N0001`, 엣지 `E0001`)

---

### Stage C: 분석 및 속성 부착

**목적:** 네트워크에 DEM 고도, 경사도, 난이도, 인접 정상 정보를 추가합니다.

```bash
python scripts/c_analyze/run_pipeline.py
```

- 입력: `trail_network_edges.geojson`, `trail_network_nodes.geojson` (Stage B)
- 입력: `korea_dem.tif`, `standard_summit.geojson` (Stage A)
- 출력:
    - `data/interim/c_output/node_with_elevation.geojson`
    - `data/interim/c_output/final_trail_dataset.geojson`
    - `data/interim/c_output/summit_points.geojson`
    - `data/interim/c_output/quality_report.md`

**주요 계산:**

| 항목 | 계산 방식 |
|------|-----------|
| 고도 | DEM GeoTIFF에서 좌표 샘플링 |
| 경사도 | `(고도차 / 거리) × 100` (%) |
| 난이도 | easy < 5% ≤ medium < 12% ≤ hard |
| 정상 연결 | 엣지 중간점 기준 300m 이내 최근접 정상 |

---

### (선택) DB 적재

```bash
python scripts/c_analyze/load_to_postgis.py
```

- 입력: `node_with_elevation.geojson`, `final_trail_dataset.geojson`, `summit_points.geojson`
- PostGIS 테이블: `trail_nodes`, `trail_edges`, `summit_points`
- 실행 전 PostgreSQL + PostGIS 설치 및 환경 변수 설정이 필요합니다.

---

## 5. 단계별 검증 방법

### Stage A 검증

#### A-1 (OSM 추출) 검증

```bash
# 피처 수 확인
python -c "
import json
with open('data/interim/a_output/standard_osm_trail.geojson') as f:
    data = json.load(f)
print('등산로 피처 수:', len(data['features']))
print('첫 번째 ID:', data['features'][0]['properties'].get('trail_id'))
"

# 정상 수 확인
python -c "
import json
with open('data/interim/a_output/standard_summit.geojson') as f:
    data = json.load(f)
print('정상 피처 수:', len(data['features']))
"
```

**확인 포인트:**
- [ ] `trail_id` 필드가 `T0001` 형식인지
- [ ] `source` 필드가 `"osm"`인지
- [ ] 좌표계가 WGS84 (경도 124~132, 위도 33~39 범위)인지
- [ ] `geometry_hash` 중복이 없는지

#### A-2 (산림청 변환) 검증

```bash
python -c "
import json
with open('data/interim/a_output/standard_public_trail.geojson') as f:
    data = json.load(f)
ids = [f['properties']['trail_id'] for f in data['features']]
print('공식 등산로 수:', len(data['features']))
print('중복 ID 수:', len(ids) - len(set(ids)))
print('샘플 좌표:', data['features'][0]['geometry']['coordinates'][0])
"
```

**확인 포인트:**
- [ ] `trail_id` 형식이 `PUBLIC_TRAIL_000001`인지
- [ ] 좌표가 WGS84 범위 내인지 (변환 오류 확인)
- [ ] `distance_m`이 0보다 큰지

#### A-3 (병합) 검증

```bash
python scripts/a_collect/validate_a_outputs.py
```

또는 수동 확인:

```bash
python -c "
import json
with open('data/interim/a_output/standard_trail.geojson') as f:
    data = json.load(f)
sources = {}
for f in data['features']:
    s = f['properties'].get('source', 'unknown')
    sources[s] = sources.get(s, 0) + 1
print('소스별 피처 수:', sources)
print('전체 피처 수:', len(data['features']))
"
```

**확인 포인트:**
- [ ] 소스별 피처 수가 합리적인지 (public + osm 혼합)
- [ ] 중복 피처가 없는지 (`geometry_hash` 기준)
- [ ] `merge_status` 필드에 `merged/kept/added` 값이 있는지

---

### Stage B 검증

```bash
# 노드·엣지 수 및 ID 형식 확인
python -c "
import json
with open('data/interim/b_output/trail_network_edges.geojson') as f:
    edges = json.load(f)
with open('data/interim/b_output/trail_network_nodes.geojson') as f:
    nodes = json.load(f)

print('엣지 수:', len(edges['features']))
print('노드 수:', len(nodes['features']))

# 참조 무결성: 엣지의 노드 ID가 실제 존재하는지
node_ids = {n['properties']['node_id'] for n in nodes['features']}
missing = 0
for e in edges['features']:
    p = e['properties']
    if p.get('start_node_id') not in node_ids:
        missing += 1
    if p.get('end_node_id') not in node_ids:
        missing += 1
print('참조 불일치 수:', missing)
"
```

**확인 포인트:**
- [ ] 엣지 ID 형식이 `E0001`인지
- [ ] 노드 ID 형식이 `N0001`인지
- [ ] `node_type`이 `junction / start / end` 중 하나인지
- [ ] `start_node_id`, `end_node_id` 모두 노드 파일에 존재하는지 (참조 무결성)
- [ ] `distance_m`이 모두 0보다 큰지
- [ ] 중복 `edge_id`가 없는지

---

### Stage C 검증

```bash
# quality_report.md 확인 (파이프라인 실행 후 자동 생성)
cat data/interim/c_output/quality_report.md
```

추가 수동 검증:

```bash
python -c "
import json
with open('data/interim/c_output/final_trail_dataset.geojson') as f:
    data = json.load(f)

total = len(data['features'])
qa_pass = sum(1 for f in data['features'] if f['properties'].get('qa_status') == 'pass')
no_elev = sum(1 for f in data['features'] if f['properties'].get('elevation_start_m') is None)
has_summit = sum(1 for f in data['features'] if f['properties'].get('nearest_summit_id'))

print(f'전체 엣지: {total}')
print(f'QA 통과: {qa_pass} ({qa_pass/total*100:.1f}%)')
print(f'고도 누락: {no_elev}')
print(f'정상 연결: {has_summit} ({has_summit/total*100:.1f}%)')
"
```

```bash
python -c "
import json
with open('data/interim/c_output/node_with_elevation.geojson') as f:
    data = json.load(f)

statuses = {}
for f in data['features']:
    s = f['properties'].get('elevation_status', 'unknown')
    statuses[s] = statuses.get(s, 0) + 1
print('고도 샘플링 상태:', statuses)
"
```

또는 검증 스크립트 실행:

```bash
python scripts/c_analyze/validate_outputs.py
```

**확인 포인트:**
- [ ] `qa_status == "pass"` 비율이 95% 이상인지
- [ ] `elevation_status == "ok"` 비율이 90% 이상인지
- [ ] `slope_percent` 절댓값이 100%를 초과하는 이상값이 없는지
- [ ] `difficulty`가 `easy / medium / hard` 중 하나인지
- [ ] `nearest_summit_id`가 존재하는 엣지가 합리적인 비율인지

---

### 전체 파이프라인 빠른 검증

```bash
python scripts/common/check_env.py
```

---

## 6. 주요 상수 및 설정

> `scripts/c_analyze/config.py` 참고

| 상수 | 값 | 설명 |
|------|----|------|
| `SUMMIT_DEFAULT_RADIUS_M` | 30m | 정상 기본 반경 |
| `SUMMIT_LINK_MAX_DISTANCE_M` | 300m | 정상 연결 최대 거리 |
| `ABNORMAL_SLOPE_THRESHOLD` | 100% | 비정상 경사도 임계값 |
| `EASY_MAX_EXCLUSIVE` | 5% | easy 난이도 상한 |
| `MEDIUM_MAX_EXCLUSIVE` | 12% | medium 난이도 상한 |

> `scripts/b_normalize/build_network.py` 내부 상수

| 상수 | 값 | 설명 |
|------|----|------|
| 중복 엣지 허용 오차 | 5m | 동일 엣지 판별 기준 |
| 고립·단말 엣지 제거 임계값 | 20m | 이 거리 미만 단말 엣지 제거 |
| 병합 격자 크기 (Stage A) | 500m | 공간 인덱스 셀 크기 |
| OSM 커버리지 임계값 (Stage A) | 60% | 이 미만이면 OSM 보완 |

---

## 7. 산출물 스펙

### 노드 (`trail_network_nodes.geojson`, `node_with_elevation.geojson`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `node_id` | string | `N0001` 형식 |
| `node_type` | string | `junction / start / end` |
| `degree` | int | 연결된 엣지 수 |
| `elevation_m` | float \| null | DEM 샘플링 고도 (m) |
| `elevation_status` | string | `ok / nodata / out_of_bounds / error` |

### 엣지 (`final_trail_dataset.geojson`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `edge_id` | string | `E0001` 형식 |
| `start_node_id` | string | 시작 노드 ID |
| `end_node_id` | string | 끝 노드 ID |
| `distance_m` | float | 엣지 길이 (m) |
| `is_bidirectional` | bool | 양방향 여부 |
| `elevation_start_m` | float \| null | 시작점 고도 |
| `elevation_end_m` | float \| null | 끝점 고도 |
| `elevation_diff_m` | float \| null | 고도차 (끝 - 시작) |
| `slope_percent` | float \| null | 경사도 (%) |
| `difficulty` | string \| null | `easy / medium / hard` |
| `nearest_summit_id` | string \| null | 가장 가까운 정상 ID |
| `qa_status` | string | `pass / fail` |

### 정상 (`summit_points.geojson`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `summit_id` | string | `S0001` 형식 |
| `name` | string | 정상 이름 |
| `elevation_m` | float \| null | 고도 (m) |
| `source` | string | `osm / public` |
| `radius_m` | float | 기본값 30m |

---

## 8. 100대 명산 edgeIds 재매핑 절차

`top100mountains.json`의 `edgeIds`가 DB 재적재 등으로 깨졌을 때 재매핑하는 방법입니다.

### 실행 조건

- Stage B 산출물 `trail_network_edges.geojson`이 `data/interim/b_output/`에 존재해야 합니다.
- `top100mountains.json`이 `src/main/resources/data/`에 존재해야 합니다.

### 실행 방법

```bash
# backend/python/ 디렉터리에서 실행
python scripts/c_analyze/remap_edgeids.py
```

### 출력

- `src/main/resources/data/top100mountains_remapped.json` 생성
- 매핑된 산 / 매핑 실패 산 목록 터미널에 출력

### 반영 방법

결과를 확인한 후 `top100mountains.json`에 덮어씁니다.

```bash
# Windows
copy src\main\resources\data\top100mountains_remapped.json src\main\resources\data\top100mountains.json

# macOS/Linux
cp src/main/resources/data/top100mountains_remapped.json src/main/resources/data/top100mountains.json
```

### 주의사항

- 자동 매핑 기준(`mountain_name` exact match + 좌표 최단거리)으로 확정된 edge만 반영합니다.
- 매핑 실패 산은 `edgeIds: []`로 유지됩니다.
- 덮어쓰기 전 반드시 remapped JSON의 edge_id가 DB에 존재하는지 검증하세요.

---

## 9. 트러블슈팅

### PROJ 관련 오류

```
CRS 초기화 오류, pyproj 관련 에러
```

`config.py` 상단에 아래 코드가 설정되어 있는지 확인:

```python
import pyproj
os.environ["PROJ_LIB"] = pyproj.datadir.get_data_dir()
```

### DEM 샘플링 실패 (`out_of_bounds` 다량 발생)

- `korea_dem.tif`의 커버리지 범위 확인
- 노드 좌표가 WGS84 경위도인지 확인 (평면직각좌표 혼입 가능성)

### 병합 후 피처 수가 예상보다 적음

- Stage A 병합 로그에서 커버리지 비율 확인
- OSM 60% 임계값 조정 여부를 팀과 협의

### Stage B 이후 노드 수가 매우 많음

- `build_network.py`의 degree-2 병합 조건이 지나치게 엄격한지 확인
- `surface`, `trail_type` 속성 불일치로 병합이 안 될 수 있음

### DB 적재 실패

- PostGIS 익스텐션 활성화 확인: `CREATE EXTENSION postgis;`
- 환경 변수 확인: `echo $ORDA_DB_HOST`
- 테이블 이미 존재 시 스크립트가 DROP 후 재생성하므로 기존 데이터 유실에 주의
