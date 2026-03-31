package com.orda.backend.domain.hiking.model;

import com.orda.backend.domain.hiking.entity.GpsTrack.ElevationSource;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CanonicalGpsPoint {

    private final double snappedLatitude;
    private final double snappedLongitude;
    private final Double canonicalElevationM;
    private final ElevationSource elevationSource;
}