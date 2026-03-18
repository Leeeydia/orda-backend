package com.orda.backend.domain.hiking.repository;

public interface NearestSummitResult {
    String getSummit_id();
    String getName();
    Double getRadius_m();
    Double getLatitude();
    Double getLongitude();
    Double getDistance_m();
}