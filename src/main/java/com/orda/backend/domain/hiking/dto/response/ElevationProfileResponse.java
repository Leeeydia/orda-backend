package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ElevationProfileResponse {

    private final Long sessionId;
    private final ElevationProfileSummaryResponse summary;
    private final List<ElevationProfilePointResponse> points;
}