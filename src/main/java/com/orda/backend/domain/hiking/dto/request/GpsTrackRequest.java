package com.orda.backend.domain.hiking.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GpsTrackRequest {

    private Double latitude;
    private Double longitude;
    private Double elevationM;
    private Double accuracyM;
}