package com.orda.backend.domain.trail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TrailNearbyResponse {

    private final boolean nearTrail;
    private final double distanceM;
}