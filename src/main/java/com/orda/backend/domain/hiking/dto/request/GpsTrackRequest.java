package com.orda.backend.domain.hiking.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsTrackRequest {

    private Double latitude;       // raw GPS 위도
    private Double longitude;      // raw GPS 경도
    private Double elevationM;     // raw GPS 고도 (null 허용)
    private Double accuracyM;      // GPS 정확도 (null 허용)
}