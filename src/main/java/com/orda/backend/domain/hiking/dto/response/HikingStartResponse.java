package com.orda.backend.domain.hiking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class HikingStartResponse {

    private Long sessionId;
    private LocalDateTime startedAt;
    private List<NearbySummitItem> nearbySummits;
}