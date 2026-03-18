package com.orda.backend.domain.summit.repository;

public interface NearestSummitResult {
    String getSummit_id();
    String getName();
    Double getRadius_m();
    Double getLatitude();
    Double getLongitude();
    Double getDistance_m();
}