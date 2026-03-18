package com.orda.backend.domain.hiking.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SummitVerifyRequest {
    private Long sessionId;
    private Double latitude;
    private Double longitude;
}