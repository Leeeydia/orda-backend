from __future__ import annotations

import os
from pathlib import Path

import pyproj
from dotenv import load_dotenv

os.environ["PROJ_LIB"] = pyproj.datadir.get_data_dir()

# ──────────────────────────────────────────────
# 기준: scripts/c_analyze/config.py
# parents[2] → backend/python/
# ──────────────────────────────────────────────
BASE_DIR = Path(__file__).resolve().parents[2]

# backend/python/.env 읽기
ENV_PATH = BASE_DIR / ".env"
load_dotenv(ENV_PATH)


def _env_path(name: str, default: Path) -> Path:
    value = os.getenv(name)
    if not value:
        return default
    return Path(value).expanduser()


# ──────────────────────────────────────────────
# 입력 경로
# ──────────────────────────────────────────────

# B단계 산출물
INPUT_B_DIR = _env_path("ORDA_INPUT_B_DIR", BASE_DIR / "data" / "interim" / "b_output")
INPUT_EDGES_PATH = _env_path("ORDA_INPUT_EDGES_PATH", INPUT_B_DIR / "trail_network_edges.geojson")
INPUT_NODES_PATH = _env_path("ORDA_INPUT_NODES_PATH", INPUT_B_DIR / "trail_network_nodes.geojson")

# DEM 원본 경로
INPUT_DEM_PATH = _env_path(
    "ORDA_INPUT_DEM_PATH",
    BASE_DIR / "data" / "raw" / "dem" / "nasadem" / "korea_dem.tif",
    )

# A단계 산출물
INPUT_A_DIR = _env_path("ORDA_INPUT_A_DIR", BASE_DIR / "data" / "interim" / "a_output")

# 정상 데이터
INPUT_SUMMIT_PATH = _env_path("ORDA_INPUT_SUMMIT_PATH", INPUT_A_DIR / "standard_summit.geojson")

# 공공 등산로 (surface 포함)
INPUT_PUBLIC_TRAILS_PATH = _env_path(
    "ORDA_INPUT_PUBLIC_TRAILS_PATH",
    INPUT_A_DIR / "standard_public_trail.geojson",
    )

# ──────────────────────────────────────────────
# 출력 경로
# ──────────────────────────────────────────────
DATA_DIR = _env_path("ORDA_DATA_DIR", BASE_DIR / "data")

INTERIM_C_OUTPUT_DIR = _env_path(
    "ORDA_INTERIM_C_OUTPUT_DIR",
    DATA_DIR / "interim" / "c_output",
    )
NODE_ELEVATION_PATH = _env_path(
    "ORDA_NODE_ELEVATION_PATH",
    INTERIM_C_OUTPUT_DIR / "node_with_elevation.geojson",
    )
FINAL_TRAIL_DATASET_PATH = _env_path(
    "ORDA_FINAL_TRAIL_DATASET_PATH",
    INTERIM_C_OUTPUT_DIR / "final_trail_dataset.geojson",
    )
SUMMIT_POINTS_PATH = _env_path(
    "ORDA_SUMMIT_POINTS_PATH",
    INTERIM_C_OUTPUT_DIR / "summit_points.geojson",
    )
QUALITY_REPORT_PATH = _env_path(
    "ORDA_QUALITY_REPORT_PATH",
    INTERIM_C_OUTPUT_DIR / "quality_report.md",
    )

FINAL_DIR = _env_path("ORDA_FINAL_DIR", DATA_DIR / "final")
FINAL_TRAIL_DATASET_FINAL_PATH = _env_path(
    "ORDA_FINAL_TRAIL_DATASET_FINAL_PATH",
    FINAL_DIR / "final_trail_dataset.geojson",
    )
SUMMIT_POINTS_FINAL_PATH = _env_path(
    "ORDA_SUMMIT_POINTS_FINAL_PATH",
    FINAL_DIR / "summit_points.geojson",
    )
QUALITY_REPORT_FINAL_PATH = _env_path(
    "ORDA_QUALITY_REPORT_FINAL_PATH",
    FINAL_DIR / "quality_report.md",
    )

# ──────────────────────────────────────────────
# 상수
# ──────────────────────────────────────────────
SUMMIT_DEFAULT_RADIUS_M = 30
SUMMIT_LINK_MAX_DISTANCE_M = 300
ABNORMAL_SLOPE_THRESHOLD = 100

EASY_MAX_EXCLUSIVE = 5
MEDIUM_MAX_EXCLUSIVE = 12

# ──────────────────────────────────────────────
# PostgreSQL / Supabase 접속 설정
# ──────────────────────────────────────────────
DB_HOST = os.getenv("ORDA_DB_HOST", "localhost")
DB_PORT = int(os.getenv("ORDA_DB_PORT", "5432"))
DB_NAME = os.getenv("ORDA_DB_NAME", "orda")
DB_USER = os.getenv("ORDA_DB_USER", "postgres")
DB_PASSWORD = os.getenv("ORDA_DB_PASSWORD", "0000")

# local: disable / supabase: require
DB_SSLMODE = os.getenv("ORDA_DB_SSLMODE", "disable")

# public / extensions / gis 등 실제 설치 스키마명
POSTGIS_SCHEMA = os.getenv("ORDA_POSTGIS_SCHEMA", "public")

# 선택 옵션
DB_CONNECT_TIMEOUT = int(os.getenv("ORDA_DB_CONNECT_TIMEOUT", "10"))
DB_APPLICATION_NAME = os.getenv("ORDA_DB_APPLICATION_NAME", "orda-python-load")