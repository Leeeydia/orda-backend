from pathlib import Path

import geopandas as gpd


BASE_DIR = Path(__file__).resolve().parents[2]

BOUNDARY_PATH = BASE_DIR / "data" / "raw" / "gyeryong" / "gyeryong_boundary.geojson"
PEAK_RAW_PATH = BASE_DIR / "data" / "raw" / "gyeryong" / "gyeryong_osm_peak_raw.geojson"
OUTPUT_PATH = BASE_DIR / "data" / "interim" / "a_output" / "gyeryong_osm_peak_filtered.geojson"


def main():
    boundary_gdf = gpd.read_file(BOUNDARY_PATH)
    peak_gdf = gpd.read_file(PEAK_RAW_PATH)

    if boundary_gdf.empty:
        raise ValueError("gyeryong_boundary.geojson 파일이 비어 있습니다.")

    if peak_gdf.empty:
        raise ValueError("gyeryong_osm_peak_raw.geojson 파일이 비어 있습니다.")

    boundary_gdf = boundary_gdf.to_crs(epsg=4326)
    peak_gdf = peak_gdf.to_crs(epsg=4326)

    boundary_union = boundary_gdf.union_all()
    filtered_peak_gdf = peak_gdf[peak_gdf.within(boundary_union)].copy()

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    filtered_peak_gdf.to_file(OUTPUT_PATH, driver="GeoJSON")

    print(f"원본 peak 개수: {len(peak_gdf)}")
    print(f"경계 내부 peak 개수: {len(filtered_peak_gdf)}")
    print(f"저장 완료: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()