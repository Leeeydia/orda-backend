package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ElevationProfilePointResponse {

    private final Integer sequenceNum;
    private final double latitude;
    private final double longitude;
    private final Double elevationMeters;
    private final String elevationStatus;
    private final double segmentDistanceMeters;
    private final double cumulativeDistanceMeters;
    private final Double elevationDiffMeters;
}