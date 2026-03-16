import rasterio

from config import INPUT_DEM_PATH


def main() -> None:
    print("DEM path:", INPUT_DEM_PATH)
    print("DEM exists:", INPUT_DEM_PATH.exists())

    with rasterio.open(INPUT_DEM_PATH) as dataset:
        print("CRS:", dataset.crs)
        print("Resolution:", dataset.res)
        print("Bounds:", dataset.bounds)
        print("Width:", dataset.width)
        print("Height:", dataset.height)
        print("Count:", dataset.count)
        print("Dtypes:", dataset.dtypes)
        print("Nodata:", dataset.nodata)

        sample = next(dataset.sample([(127.0, 37.5)]))
        print("Sample elevation:", sample)


if __name__ == "__main__":
    main()