package com.orda.backend.domain.summit.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SessionVerifiedSummit {

    private final String summitId;
    private final String summitName;
    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime verifiedAt;
}