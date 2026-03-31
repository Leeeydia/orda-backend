package com.orda.backend.common.projection;

/**
 * ST_ClosestPoint 쿼리 결과 매핑용 JPA Projection
 */
public interface SnappedPointProjection {

    Double getSnappedLon();
    Double getSnappedLat();
    Double getDistanceM();
}