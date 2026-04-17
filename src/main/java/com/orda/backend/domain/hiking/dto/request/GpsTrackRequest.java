package com.orda.backend.domain.hiking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsTrackRequest {

    @NotNull(message = "sequenceNum은 필수입니다.")
    @Positive(message = "sequenceNum은 양수여야 합니다.")
    private Integer sequenceNum;   // 프론트에서 채번한 순서 번호

    private Double latitude;       // raw GPS 위도
    private Double longitude;      // raw GPS 경도
    private Double elevationM;     // raw GPS 고도 (null 허용)
    private Double accuracyM;      // GPS 정확도 (null 허용)
}