package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ElevationProfilePointResponse {

    private final Integer sequenceNum;
    private final Double latitude;
    private final Double longitude;
    private final Double elevationMeters;
    private final Double segmentDistanceMeters;
    private final Double cumulativeDistanceMeters;
    private final Double elevationDiffMeters;
}