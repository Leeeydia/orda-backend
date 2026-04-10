package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ElevationProfileSummaryResponse {

    private final double totalDistanceMeters;
    private final Double minElevationMeters;
    private final Double maxElevationMeters;
    private final Double totalElevationGainMeters;
    private final Double totalElevationLossMeters;
    private final String elevationSummaryStatus;
    private final int pointCount;
}