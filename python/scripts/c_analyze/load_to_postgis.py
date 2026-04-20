from __future__ import annotations

import json
import re
from typing import Any

import psycopg2
from psycopg2.extras import execute_values

from config import (
    DB_APPLICATION_NAME,
    DB_CONNECT_TIMEOUT,
    DB_HOST,
    DB_NAME,
    DB_PASSWORD,
    DB_PORT,
    DB_SSLMODE,
    DB_USER,
    FINAL_TRAIL_DATASET_PATH,
    NODE_ELEVATION_PATH,
    POSTGIS_SCHEMA,
    SUMMIT_POINTS_PATH,
)
from pipeline import read_geojson_features


_VALID_PG_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def validate_pg_identifier(value: str, label: str) -> str:
    """스키마명 같은 SQL 식별자를 최소 검증한다."""
    if not value or not _VALID_PG_IDENTIFIER.fullmatch(value):
        raise ValueError(f"{label} 값이 올바르지 않습니다: {value!r}")
    return value


POSTGIS_SCHEMA_SAFE = validate_pg_identifier(POSTGIS_SCHEMA, "POSTGIS_SCHEMA")


def postgis_fn(name: str) -> str:
    """PostGIS 함수명을 스키마 포함 형태로 만든다."""
    return f"{POSTGIS_SCHEMA_SAFE}.{name}"


def build_search_path() -> str:
    """public + PostGIS 스키마를 search_path로 구성한다."""
    schemas = ["public"]
    if POSTGIS_SCHEMA_SAFE not in schemas:
        schemas.append(POSTGIS_SCHEMA_SAFE)
    return ",".join(schemas)


def get_connection():
    """PostgreSQL / Supabase 접속 연결을 반환한다."""
    kwargs = {
        "host": DB_HOST,
        "port": DB_PORT,
        "dbname": DB_NAME,
        "user": DB_USER,
        "password": DB_PASSWORD,
        "connect_timeout": DB_CONNECT_TIMEOUT,
        "application_name": DB_APPLICATION_NAME,
        "options": f"-c search_path={build_search_path()}",
    }

    if DB_SSLMODE:
        kwargs["sslmode"] = DB_SSLMODE

    return psycopg2.connect(**kwargs)


# ──────────────────────────────────────────────
# 유틸
# ──────────────────────────────────────────────

def table_exists(cursor, table_name: str) -> bool:
    """public 스키마에 해당 테이블이 존재하는지 확인한다."""
    cursor.execute(
        """
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = %s
        )
        """,
        (table_name,),
    )
    return cursor.fetchone()[0]


def ensure_required_tables(cursor) -> None:
    """필수 테이블 존재 여부를 확인한다."""
    required = ["trail_nodes", "trail_edges", "summit_points"]
    missing = [table for table in required if not table_exists(cursor, table)]

    if missing:
        raise RuntimeError(
            "필수 테이블이 없습니다. 먼저 스키마를 생성하세요. "
            f"누락 테이블: {', '.join(missing)}"
        )


def build_geom_expr(param_name: str = "geom") -> str:
    """GeoJSON 문자열을 geometry로 바꾸는 SQL 조각을 반환한다."""
    return (
        f"{postgis_fn('ST_SetSRID')}("
        f"{postgis_fn('ST_GeomFromGeoJSON')}(%({param_name})s), 4326)"
    )


# ──────────────────────────────────────────────
# 적재 함수
# ──────────────────────────────────────────────

def load_nodes(cursor, features: list[dict[str, Any]]) -> int:
    """node_with_elevation.geojson → trail_nodes 테이블에 배치 적재한다."""
    sql = """
          INSERT INTO trail_nodes (
              node_id, node_type, degree,
              elevation_m, elevation_status, qa_status,
              geom
          ) VALUES %s
              ON CONFLICT (node_id) DO NOTHING \
          """

    template = (
        f"(%(node_id)s, %(node_type)s, %(degree)s, "
        f"%(elevation_m)s, %(elevation_status)s, %(qa_status)s, "
        f"{build_geom_expr('geom')})"
    )

    rows = []
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue
        if not props.get("node_id"):
            continue

        rows.append(
            {
                "node_id": props.get("node_id"),
                "node_type": props.get("node_type"),
                "degree": props.get("degree"),
                "elevation_m": props.get("elevation_m"),
                "elevation_status": props.get("elevation_status"),
                "qa_status": props.get("qa_status"),
                "geom": json.dumps(geom, ensure_ascii=False),
            }
        )

    if not rows:
        return 0

    execute_values(cursor, sql, rows, template=template, page_size=1000)
    return len(rows)


def load_edges(cursor, features: list[dict[str, Any]]) -> int:
    """final_trail_dataset.geojson → trail_edges 테이블에 배치 적재한다."""
    sql = """
          INSERT INTO trail_edges (
              edge_id, start_node_id, end_node_id,
              distance_m, elevation_start_m, elevation_end_m,
              elevation_diff_m, slope_percent, difficulty_score, difficulty,
              surface, nearest_summit_id, qa_status,
              geom
          ) VALUES %s
              ON CONFLICT (edge_id) DO NOTHING \
          """

    template = (
        f"(%(edge_id)s, %(start_node_id)s, %(end_node_id)s, "
        f"%(distance_m)s, %(elevation_start_m)s, %(elevation_end_m)s, "
        f"%(elevation_diff_m)s, %(slope_percent)s, %(difficulty_score)s, %(difficulty)s, "
        f"%(surface)s, %(nearest_summit_id)s, %(qa_status)s, "
        f"{build_geom_expr('geom')})"
    )

    rows = []
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue
        if not props.get("edge_id"):
            continue

        rows.append(
            {
                "edge_id": props.get("edge_id"),
                "start_node_id": props.get("start_node_id"),
                "end_node_id": props.get("end_node_id"),
                "distance_m": props.get("distance_m"),
                "elevation_start_m": props.get("elevation_start_m"),
                "elevation_end_m": props.get("elevation_end_m"),
                "elevation_diff_m": props.get("elevation_diff_m"),
                "slope_percent": props.get("slope_percent"),
                "difficulty_score": props.get("difficulty_score"),
                "difficulty": props.get("difficulty"),
                "surface": props.get("surface"),
                "nearest_summit_id": props.get("nearest_summit_id"),
                "qa_status": props.get("qa_status"),
                "geom": json.dumps(geom, ensure_ascii=False),
            }
        )

    if not rows:
        return 0

    execute_values(cursor, sql, rows, template=template, page_size=1000)
    return len(rows)


def load_summits(cursor, features: list[dict[str, Any]]) -> int:
    """summit_points.geojson → summit_points 테이블에 배치 적재한다."""
    sql = """
          INSERT INTO summit_points (
              summit_id, name, elevation_m,
              source, radius_m,
              geom
          ) VALUES %s
              ON CONFLICT (summit_id) DO NOTHING \
          """

    template = (
        f"(%(summit_id)s, %(name)s, %(elevation_m)s, "
        f"%(source)s, %(radius_m)s, {build_geom_expr('geom')})"
    )

    rows = []
    for feat in features:
        props = feat.get("properties", {})
        geom = feat.get("geometry")

        if not geom:
            continue
        if not props.get("summit_id"):
            continue

        rows.append(
            {
                "summit_id": props.get("summit_id"),
                "name": props.get("name"),
                "elevation_m": props.get("elevation_m"),
                "source": props.get("source"),
                "radius_m": props.get("radius_m"),
                "geom": json.dumps(geom, ensure_ascii=False),
            }
        )

    if not rows:
        return 0

    execute_values(cursor, sql, rows, template=template, page_size=1000)
    return len(rows)


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

    cursor.execute("SELECT COUNT(*) FROM trail_nodes WHERE geom IS NULL")
    null_nodes = cursor.fetchone()[0]
    print(f"  trail_nodes geom NULL: {null_nodes}개")

    cursor.execute("SELECT COUNT(*) FROM trail_edges WHERE geom IS NULL")
    null_edges = cursor.fetchone()[0]
    print(f"  trail_edges geom NULL: {null_edges}개")

    cursor.execute("SELECT COUNT(*) FROM summit_points WHERE geom IS NULL")
    null_summits = cursor.fetchone()[0]
    print(f"  summit_points geom NULL: {null_summits}개")

    cursor.execute("SELECT ST_SRID(geom) FROM trail_nodes LIMIT 1")
    row = cursor.fetchone()
    if row:
        print(f"  trail_nodes SRID: {row[0]}")

    cursor.execute("SELECT ST_SRID(geom) FROM trail_edges LIMIT 1")
    row = cursor.fetchone()
    if row:
        print(f"  trail_edges SRID: {row[0]}")

    cursor.execute("SELECT ST_SRID(geom) FROM summit_points LIMIT 1")
    row = cursor.fetchone()
    if row:
        print(f"  summit_points SRID: {row[0]}")

    cursor.execute(
        """
        SELECT COUNT(*)
        FROM trail_edges e
        WHERE NOT EXISTS (
            SELECT 1 FROM trail_nodes n WHERE n.node_id = e.start_node_id
        )
           OR NOT EXISTS (
            SELECT 1 FROM trail_nodes n WHERE n.node_id = e.end_node_id
        )
        """
    )
    orphan = cursor.fetchone()[0]
    print(f"  edge → node 참조 실패: {orphan}개")


# ──────────────────────────────────────────────
# 메인
# ──────────────────────────────────────────────

def main() -> None:
    print("[PostGIS 적재] 시작...")

    print("\n[1] GeoJSON 로딩")
    node_features = read_geojson_features(NODE_ELEVATION_PATH)
    edge_features = read_geojson_features(FINAL_TRAIL_DATASET_PATH)
    summit_features = read_geojson_features(SUMMIT_POINTS_PATH)

    print(f"  nodes: {len(node_features)}개")
    print(f"  edges: {len(edge_features)}개")
    print(f"  summits: {len(summit_features)}개")

    print("\n[2] DB 접속 및 적재")
    conn = get_connection()
    cursor = conn.cursor()

    try:
        ensure_required_tables(cursor)

        print("  기존 데이터 초기화...")

        if table_exists(cursor, "summit_verifications"):
            cursor.execute("TRUNCATE summit_verifications RESTART IDENTITY")
            print("    summit_verifications 초기화 완료")

        cursor.execute("TRUNCATE trail_edges, trail_nodes, summit_points RESTART IDENTITY CASCADE")
        print("    trail 테이블 초기화 완료")

        node_count = load_nodes(cursor, node_features)
        print(f"  trail_nodes 적재: {node_count}건")

        edge_count = load_edges(cursor, edge_features)
        print(f"  trail_edges 적재: {edge_count}건")

        summit_count = load_summits(cursor, summit_features)
        print(f"  summit_points 적재: {summit_count}건")

        verify_counts(cursor)
        verify_spatial(cursor)

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