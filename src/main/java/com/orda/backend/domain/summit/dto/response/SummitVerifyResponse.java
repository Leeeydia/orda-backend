package com.orda.backend.domain.summit.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SummitVerifyResponse {
    private boolean verified;
    private String summitId;
    private String summitName;
    private Double distanceM;
    private String verificationMethod;
    private String photoPath;
    private String aiRecognizedName;
    private String aiRecognizedElevation;
    private String aiReason;
}