package com.orda.backend.domain.hiking.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SnappedPoint {

    private final double latitude;
    private final double longitude;
    private final double distanceM;   // raw → snapped 거리 (미터)
    private final boolean snapped;    // false면 스냅 실패 → raw 좌표 그대로 사용
}