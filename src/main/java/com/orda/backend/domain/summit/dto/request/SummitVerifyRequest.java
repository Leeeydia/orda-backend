package com.orda.backend.domain.summit.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SummitVerifyRequest {
    private Long sessionId;
    private Double latitude;
    private Double longitude;
}