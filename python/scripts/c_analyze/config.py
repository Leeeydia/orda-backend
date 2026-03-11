from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]

DATA_DIR = BASE_DIR / "data"

RAW_DIR = DATA_DIR / "raw"
STANDARD_DIR = DATA_DIR / "standard"
NETWORK_DIR = DATA_DIR / "network"
INTERIM_C_OUTPUT_DIR = DATA_DIR / "interim" / "c_output"
FINAL_DIR = DATA_DIR / "final"

# 실데이터 입력 경로
INPUT_EDGES_PATH = NETWORK_DIR / "trail_network_edges.geojson"
INPUT_NODES_PATH = NETWORK_DIR / "trail_network_nodes.geojson"
INPUT_DEM_PATH = RAW_DIR / "dem_raw.tif"
INPUT_SUMMIT_SOURCE_PATH = STANDARD_DIR / "standard_summit.geojson"

# 개발 중간 산출물
FINAL_TRAIL_DATASET_PATH = INTERIM_C_OUTPUT_DIR / "final_trail_dataset.geojson"
SUMMIT_POINTS_PATH = INTERIM_C_OUTPUT_DIR / "summit_points.geojson"
QUALITY_REPORT_PATH = INTERIM_C_OUTPUT_DIR / "quality_report.md"

# 문서 기준 최종 산출물 경로
FINAL_TRAIL_DATASET_FINAL_PATH = FINAL_DIR / "final_trail_dataset.geojson"
SUMMIT_POINTS_FINAL_PATH = FINAL_DIR / "summit_points.geojson"
QUALITY_REPORT_FINAL_PATH = FINAL_DIR / "quality_report.md"

SUMMIT_DEFAULT_RADIUS_M = 30
SUMMIT_LINK_MAX_DISTANCE_M = 300
ABNORMAL_SLOPE_THRESHOLD = 100

EASY_MAX_EXCLUSIVE = 5
MEDIUM_MAX_EXCLUSIVE = 12