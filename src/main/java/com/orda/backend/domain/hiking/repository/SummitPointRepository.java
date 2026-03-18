package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.SummitPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SummitPointRepository extends JpaRepository<SummitPoint, String> {

    @Query(value = """
            SELECT summit_id, name, elevation_m, radius_m,
                   ST_X(geom) AS longitude,
                   ST_Y(geom) AS latitude,
                   ST_Distance(
                       geom::geography,
                       ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                   ) AS distance_m
            FROM summit_points
            ORDER BY distance_m
            LIMIT 1
            """, nativeQuery = true)
    Optional<NearestSummitResult> findNearestSummit(@Param("lat") Double lat, @Param("lng") Double lng);
}