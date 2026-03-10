from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]

DATA_DIR = BASE_DIR / "data"
MOCK_C_STAGE_DIR = DATA_DIR / "mock" / "c_stage"
INTERIM_C_OUTPUT_DIR = DATA_DIR / "interim" / "c_output"
FINAL_DIR = DATA_DIR / "final"

INPUT_EDGES_PATH = MOCK_C_STAGE_DIR / "mock_trail_network_edges.geojson"
INPUT_NODES_PATH = MOCK_C_STAGE_DIR / "mock_trail_network_nodes.geojson"
INPUT_ELEVATION_MAP_PATH = MOCK_C_STAGE_DIR / "mock_node_elevation_map.json"
INPUT_SUMMIT_SOURCE_PATH = MOCK_C_STAGE_DIR / "mock_summit_source.geojson"

FINAL_TRAIL_DATASET_PATH = INTERIM_C_OUTPUT_DIR / "final_trail_dataset.geojson"
SUMMIT_POINTS_PATH = INTERIM_C_OUTPUT_DIR / "summit_points.geojson"
QUALITY_REPORT_PATH = INTERIM_C_OUTPUT_DIR / "quality_report.md"

SUMMIT_DEFAULT_RADIUS_M = 30
SUMMIT_LINK_MAX_DISTANCE_M = 300
ABNORMAL_SLOPE_THRESHOLD = 100

EASY_MAX_EXCLUSIVE = 5
MEDIUM_MAX_EXCLUSIVE = 12