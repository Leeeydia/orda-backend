package com.orda.backend.domain.hiking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class EnrichedTrackPoint {

    private final Integer sequenceNum;
    private final double latitude;
    private final double longitude;
    private final Double elevationM;
    private final LocalDateTime recordedAt;
    private final double distanceFromPrevM;
    private final double distanceFromStartM;
    private final Long actualElapsedSeconds;
}