package com.orda.backend.domain.trail.repository;

import com.orda.backend.common.projection.SnappedPointProjection;
import com.orda.backend.domain.trail.entity.TrailEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrailEdgeRepository extends JpaRepository<TrailEdge, String> {

    List<TrailEdge> findByNearestSummitId(String summitId);

    @Query(value = """
        SELECT 
            edge_id,
            difficulty,
            difficulty_score,
            ST_AsGeoJSON(geom)::text AS geom_json
        FROM trail_edges
        WHERE geom IS NOT NULL
          AND difficulty IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> findAllEdgesWithGeom();

    @Query(value = """
        SELECT 
            edge_id,
            difficulty,
            difficulty_score,
            ST_AsGeoJSON(geom)::text AS geom_json
        FROM trail_edges
        WHERE nearest_summit_id = :summitId
          AND geom IS NOT NULL
          AND difficulty IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> findEdgesWithGeomBySummitId(@Param("summitId") String summitId);

    // bbox 기반 필터링 쿼리 추가
    @Query(value = """
        SELECT
            edge_id,
            difficulty,
            difficulty_score,
            ST_AsGeoJSON(geom)::text AS geom_json
        FROM trail_edges
        WHERE geom IS NOT NULL
          AND difficulty IS NOT NULL
          AND ST_Intersects(
              geom,
              ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
          )
        """, nativeQuery = true)
    List<Object[]> findEdgesWithGeomByBbox(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat
    );

    // edgeIds 목록 기반 난이도 지도 조회 쿼리 추가
    @Query(value = """
        SELECT
            edge_id,
            difficulty,
            difficulty_score,
            ST_AsGeoJSON(geom)::text AS geom_json
        FROM trail_edges
        WHERE edge_id = ANY(:edgeIds)
          AND geom IS NOT NULL
          AND difficulty IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> findEdgesWithGeomByEdgeIds(@Param("edgeIds") String[] edgeIds);

    // ── GPS 스냅용 ────────────────────────────────────────────
    @Query(value = """
            SELECT
                ST_X(ST_ClosestPoint(e.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))) AS snappedLon,
                ST_Y(ST_ClosestPoint(e.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))) AS snappedLat,
                ST_Distance(
                    e.geom::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) AS distanceM
            FROM trail_edges e
            WHERE ST_DWithin(
                e.geom::geography,
                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                :radiusM
            )
            ORDER BY distanceM
            LIMIT 1
            """, nativeQuery = true)
    Optional<SnappedPointProjection> findClosestPoint(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusM") double radiusM
    );
}