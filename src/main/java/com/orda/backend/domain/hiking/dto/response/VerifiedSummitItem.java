package com.orda.backend.domain.hiking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VerifiedSummitItem {

    private final String summitId;
    private final String summitName;
    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime verifiedAt;
    private final Long verifiedElapsedSec;
}