package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReplaySummaryResponse {

    private final double totalDistanceMeters;
    private final Double totalElevationGainMeters;
    private final Double totalElevationLossMeters;
    private final int totalElapsedSeconds;
    private final String elevationSummaryStatus;
}