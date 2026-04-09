package com.orda.backend.domain.hiking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class EnrichedTrackPoint {

    public enum ElevationStatus {
        DEM,
        INTERPOLATED,
        MISSING,
        GAP
    }

    private final Integer sequenceNum;
    private final double latitude;
    private final double longitude;
    private final Double elevationM;
    private final ElevationStatus elevationStatus;
    private final LocalDateTime recordedAt;
    private final double distanceFromPrevM;
    private final double distanceFromStartM;
    private final Long actualElapsedSeconds;
}