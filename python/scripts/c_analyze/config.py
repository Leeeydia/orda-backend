import os
import pyproj
os.environ["PROJ_LIB"] = pyproj.datadir.get_data_dir()

from pathlib import Path

# ──────────────────────────────────────────────
# 기준: scripts/c_analyze/config.py
# parents[2] → backend/python/
# ──────────────────────────────────────────────
BASE_DIR = Path(__file__).resolve().parents[2]

# ──────────────────────────────────────────────
# 입력 경로
# ──────────────────────────────────────────────
# B단계 산출물 (현재는 mock 경로 사용)
INPUT_B_DIR = BASE_DIR / "data" / "interim" / "b_output"
INPUT_EDGES_PATH = INPUT_B_DIR / "trail_network_edges.geojson"
INPUT_NODES_PATH = INPUT_B_DIR / "trail_network_nodes.geojson"

# DEM 원본 경로
INPUT_DEM_PATH = BASE_DIR / "data" / "raw" / "dem" / "nasadem" / "korea_dem.tif"

# 정상 데이터 경로 (A단계 산출물 - 지금은 mock 사용)
INPUT_A_DIR = BASE_DIR / "data" / "interim" / "a_output"
INPUT_SUMMIT_PATH = INPUT_A_DIR / "standard_summit.geojson"

# ──────────────────────────────────────────────
# 출력 경로 (data/ 폴더 안에 있음)
# ──────────────────────────────────────────────
DATA_DIR = BASE_DIR / "data"

# 개발 중간 산출물
INTERIM_C_OUTPUT_DIR = DATA_DIR / "interim" / "c_output"
NODE_ELEVATION_PATH = INTERIM_C_OUTPUT_DIR / "node_with_elevation.geojson"
FINAL_TRAIL_DATASET_PATH = INTERIM_C_OUTPUT_DIR / "final_trail_dataset.geojson"
SUMMIT_POINTS_PATH = INTERIM_C_OUTPUT_DIR / "summit_points.geojson"
QUALITY_REPORT_PATH = INTERIM_C_OUTPUT_DIR / "quality_report.md"

# 문서 기준 최종 산출물 경로 (팀 합의 후 여기로 복사)
FINAL_DIR = DATA_DIR / "final"
FINAL_TRAIL_DATASET_FINAL_PATH = FINAL_DIR / "final_trail_dataset.geojson"
SUMMIT_POINTS_FINAL_PATH = FINAL_DIR / "summit_points.geojson"
QUALITY_REPORT_FINAL_PATH = FINAL_DIR / "quality_report.md"

# ──────────────────────────────────────────────
# 상수
# ──────────────────────────────────────────────
SUMMIT_DEFAULT_RADIUS_M = 30
SUMMIT_LINK_MAX_DISTANCE_M = 300
ABNORMAL_SLOPE_THRESHOLD = 100

# 난이도 기준 (slope_percent 절댓값 기준)
# 0 이상 ~ 5 미만: easy
# 5 이상 ~ 12 미만: medium
# 12 이상: hard
EASY_MAX_EXCLUSIVE = 5
MEDIUM_MAX_EXCLUSIVE = 12

# ──────────────────────────────────────────────
# PostgreSQL 접속 설정
# ──────────────────────────────────────────────
import os

DB_HOST = os.environ.get("ORDA_DB_HOST", "localhost")
DB_PORT = os.environ.get("ORDA_DB_PORT", "5432")
DB_NAME = os.environ.get("ORDA_DB_NAME", "orda")
DB_USER = os.environ.get("ORDA_DB_USER", "postgres")
DB_PASSWORD = os.environ.get("ORDA_DB_PASSWORD", "0000")