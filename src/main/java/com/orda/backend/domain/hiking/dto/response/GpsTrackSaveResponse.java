package com.orda.backend.domain.hiking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GpsTrackSaveResponse {

    private Double canonicalElevationM;
    private String elevationSource;
}