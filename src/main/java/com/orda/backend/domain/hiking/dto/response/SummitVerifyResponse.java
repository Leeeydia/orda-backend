package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SummitVerifyResponse {
    private boolean verified;
    private String summitId;
    private String summitName;
    private Double distanceM;
}