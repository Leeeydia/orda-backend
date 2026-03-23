package com.orda.backend.domain.trail.repository;

import com.orda.backend.domain.trail.entity.TrailEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}