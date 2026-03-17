package com.orda.backend.domain.hiking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class HikingStartResponse {

    private Long sessionId;
    private LocalDateTime startedAt;
}