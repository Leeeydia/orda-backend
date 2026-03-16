from __future__ import annotations

import json
from typing import Any

import psycopg2

from config import (
    DB_HOST,
    DB_PORT,
    DB_NAME,
    DB_USER,
    DB_PASSWORD,
    NODE_ELEVATION_PATH,
    FINAL_TRAIL_DATASET_PATH,
    SUMMIT_POINTS_PATH,
)
from pipeline import read_geojson_features


def get_connection():
    """PostgreSQL 접속 연결을 반환한다."""
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
    )


# ──────────────────────────────────────────────
# 적재 함수
# ──────────────────────────────────────────────

def load_nodes(cursor, features: list[dict[str, Any]]) -> int:
    """node_with_elevation.geojson → trail_nodes 테이블에 적재한다."""
    sql = """
          INSERT INTO trail_nodes (
              node_id, node_type, degree,
              elevation_m, elevation_status, qa_status,
              geom
          ) VALUES (
                       %s, %s, %s,
                       %s, %s, %s,
                       ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
                   )
              ON CONFLICT (node_id) DO NOTHING \
          """

    count = 0
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue

        cursor.execute(sql, (
            props.get("node_id"),
            props.get("node_type"),
            props.get("degree"),
            props.get("elevation_m"),
            props.get("elevation_status"),
            props.get("qa_status"),
            json.dumps(geom),
        ))
        count += 1

    return count


def load_edges(cursor, features: list[dict[str, Any]]) -> int:
    """final_trail_dataset.geojson → trail_edges 테이블에 적재한다."""
    sql = """
          INSERT INTO trail_edges (
              edge_id, start_node_id, end_node_id,
              distance_m, elevation_start_m, elevation_end_m,
              elevation_diff_m, slope_percent, difficulty,
              nearest_summit_id, qa_status,
              geom
          ) VALUES (
                       %s, %s, %s,
                       %s, %s, %s,
                       %s, %s, %s,
                       %s, %s,
                       ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
                   )
              ON CONFLICT (edge_id) DO NOTHING \
          """

    count = 0
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue

        cursor.execute(sql, (
            props.get("edge_id"),
            props.get("start_node_id"),
            props.get("end_node_id"),
            props.get("distance_m"),
            props.get("elevation_start_m"),
            props.get("elevation_end_m"),
            props.get("elevation_diff_m"),
            props.get("slope_percent"),
            props.get("difficulty"),
            props.get("nearest_summit_id"),
            props.get("qa_status"),
            json.dumps(geom),
        ))
        count += 1

    return count


def load_summits(cursor, features: list[dict[str, Any]]) -> int:
    """summit_points.geojson → summit_points 테이블에 적재한다."""
    sql = """
          INSERT INTO summit_points (
              summit_id, name, elevation_m,
              source, radius_m,
              geom
          ) VALUES (
                       %s, %s, %s,
                       %s, %s,
                       ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
                   )
              ON CONFLICT (summit_id) DO NOTHING \
          """

    count = 0
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue

        cursor.execute(sql, (
            props.get("summit_id"),
            props.get("name"),
            props.get("elevation_m"),
            props.get("source"),
            props.get("radius_m"),
            json.dumps(geom),
        ))
        count += 1

    return count


# ──────────────────────────────────────────────
# 적재 후 검증
# ──────────────────────────────────────────────

def verify_counts(cursor) -> None:
    """적재 후 각 테이블 row 수를 출력한다."""
    tables = ["trail_nodes", "trail_edges", "summit_points"]

    print("\n[적재 검증]")
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        print(f"  {table}: {count}행")


def verify_spatial(cursor) -> None:
    """공간 데이터가 정상인지 간단히 확인한다."""
    print("\n[공간 데이터 검증]")

    # node geometry null 체크
    cursor.execute("SELECT COUNT(*) FROM trail_nodes WHERE geom IS NULL")
    null_nodes = cursor.fetchone()[0]
    print(f"  trail_nodes geom NULL: {null_nodes}개")

    # edge geometry null 체크
    cursor.execute("SELECT COUNT(*) FROM trail_edges WHERE geom IS NULL")
    null_edges = cursor.fetchone()[0]
    print(f"  trail_edges geom NULL: {null_edges}개")

    # SRID 확인 (샘플 1개)
    cursor.execute("SELECT ST_SRID(geom) FROM trail_nodes LIMIT 1")
    row = cursor.fetchone()
    if row:
        print(f"  trail_nodes SRID: {row[0]}")

    cursor.execute("SELECT ST_SRID(geom) FROM trail_edges LIMIT 1")
    row = cursor.fetchone()
    if row:
        print(f"  trail_edges SRID: {row[0]}")

    # 외래키 정합성: edge의 node 참조가 실제로 존재하는지
    cursor.execute("""
                   SELECT COUNT(*) FROM trail_edges e
                   WHERE NOT EXISTS (
                       SELECT 1 FROM trail_nodes n WHERE n.node_id = e.start_node_id
                   )
                      OR NOT EXISTS (
                       SELECT 1 FROM trail_nodes n WHERE n.node_id = e.end_node_id
                   )
                   """)
    orphan = cursor.fetchone()[0]
    print(f"  edge → node 참조 실패: {orphan}개")


# ──────────────────────────────────────────────
# 메인
# ──────────────────────────────────────────────

def main() -> None:
    print("[PostGIS 적재] 시작...")

    # 1. GeoJSON 로딩
    print("\n[1] GeoJSON 로딩")
    node_features = read_geojson_features(NODE_ELEVATION_PATH)
    edge_features = read_geojson_features(FINAL_TRAIL_DATASET_PATH)
    summit_features = read_geojson_features(SUMMIT_POINTS_PATH)

    print(f"  nodes: {len(node_features)}개")
    print(f"  edges: {len(edge_features)}개")
    print(f"  summits: {len(summit_features)}개")

    # 2. DB 접속 및 적재
    print("\n[2] DB 접속 및 적재")
    conn = get_connection()
    cursor = conn.cursor()

    try:
        # 기존 데이터 삭제 (재실행 시 중복 방지)
        print("  기존 데이터 초기화...")
        cursor.execute("DELETE FROM trail_edges")
        cursor.execute("DELETE FROM trail_nodes")
        cursor.execute("DELETE FROM summit_points")

        # 적재 순서: nodes → edges (외래키 때문에 순서 중요)
        node_count = load_nodes(cursor, node_features)
        print(f"  trail_nodes 적재: {node_count}건")

        edge_count = load_edges(cursor, edge_features)
        print(f"  trail_edges 적재: {edge_count}건")

        summit_count = load_summits(cursor, summit_features)
        print(f"  summit_points 적재: {summit_count}건")

        # 3. 검증
        verify_counts(cursor)
        verify_spatial(cursor)

        # 4. 커밋
        conn.commit()
        print("\n[완료] 적재 성공, 커밋됨")

    except Exception as e:
        conn.rollback()
        print(f"\n[오류] 적재 실패, 롤백됨: {e}")
        raise

    finally:
        cursor.close()
        conn.close()


if __name__ == "__main__":
    main()