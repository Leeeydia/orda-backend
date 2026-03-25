package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReplayResponse {

    private final Long sessionId;
    private final ReplaySummaryResponse summary;
    private final List<ReplayPointResponse> points;
}