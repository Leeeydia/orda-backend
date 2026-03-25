package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReplayPointResponse {

    private final double latitude;
    private final double longitude;
    private final Double elevationM;
    private final double distanceFromStartM;
    private final Long actualElapsedSeconds;
    private final Long replayElapsedSeconds;
}