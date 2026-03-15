from __future__ import annotations

import io
import json
import zipfile
from pathlib import Path
from typing import Dict, List


BASE_DIR = Path(__file__).resolve().parents[2]

OUTER_ZIP_PATH = BASE_DIR / "data" / "raw" / "forest" / "mountain.zip"
OUTPUT_DIR = BASE_DIR / "data" / "raw" / "forest" / "selected_pmntn_json"
MANIFEST_PATH = BASE_DIR / "data" / "raw" / "forest" / "selected_pmntn_manifest.json"


def is_geojson_bundle(name: str) -> bool:
    upper_name = name.upper()
    return upper_name.endswith("_GEOJSON.ZIP")


def is_target_json(name: str) -> bool:
    upper_name = Path(name).name.upper()

    if not upper_name.endswith(".JSON"):
        return False

    if not upper_name.startswith("PMNTN_"):
        return False

    if upper_name.startswith("PMNTN_SPOT_"):
        return False

    return True


def build_unique_output_path(output_dir: Path, filename: str) -> Path:
    candidate = output_dir / filename
    if not candidate.exists():
        return candidate

    stem = Path(filename).stem
    suffix = Path(filename).suffix
    index = 1

    while True:
        candidate = output_dir / f"{stem}__{index}{suffix}"
        if not candidate.exists():
            return candidate
        index += 1


def extract_target_files() -> List[Dict[str, str]]:
    if not OUTER_ZIP_PATH.exists():
        raise FileNotFoundError(f"파일을 찾을 수 없습니다: {OUTER_ZIP_PATH}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest: List[Dict[str, str]] = []

    with zipfile.ZipFile(OUTER_ZIP_PATH, "r") as outer_zip:
        for outer_info in outer_zip.infolist():
            outer_name = outer_info.filename

            if outer_info.is_dir():
                continue

            if not is_geojson_bundle(outer_name):
                continue

            inner_zip_bytes = outer_zip.read(outer_name)

            with zipfile.ZipFile(io.BytesIO(inner_zip_bytes), "r") as inner_zip:
                for inner_info in inner_zip.infolist():
                    inner_name = inner_info.filename

                    if inner_info.is_dir():
                        continue

                    if not is_target_json(inner_name):
                        continue

                    raw_bytes = inner_zip.read(inner_name)
                    output_path = build_unique_output_path(
                        OUTPUT_DIR,
                        Path(inner_name).name,
                    )

                    output_path.write_bytes(raw_bytes)

                    manifest.append(
                        {
                            "outer_zip_member": outer_name,
                            "inner_file": inner_name,
                            "output_path": output_path.as_posix(),
                        }
                    )

    return manifest


def save_manifest(manifest: List[Dict[str, str]]) -> None:
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)

    with MANIFEST_PATH.open("w", encoding="utf-8") as f:
        json.dump(
            {
                "total_count": len(manifest),
                "files": manifest,
            },
            f,
            ensure_ascii=False,
            indent=2,
        )


def main() -> None:
    print(f"BASE_DIR: {BASE_DIR}")
    print(f"입력 zip 경로: {OUTER_ZIP_PATH}")

    manifest = extract_target_files()
    save_manifest(manifest)

    print("----- 추출 완료 -----")
    print(f"입력 zip: {OUTER_ZIP_PATH}")
    print(f"추출 개수: {len(manifest)}")
    print(f"출력 폴더: {OUTPUT_DIR}")
    print(f"매니페스트: {MANIFEST_PATH}")


if __name__ == "__main__":
    main()