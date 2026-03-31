package com.orda.backend.domain.hiking.dto.response;

import com.orda.backend.domain.hiking.entity.HikingRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class HikingSessionResponse {
    private Long sessionId;
    private Long userId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Double totalDistanceM;
    private Double totalElevationGainM;
    private Double totalElevationLossM;
    private Integer totalDurationSec;
    private List<VerifiedSummitItem> verifiedSummits;

    public static HikingSessionResponse from(HikingRecord record) {
        return from(record, Collections.emptyList());
    }

    public static HikingSessionResponse from(HikingRecord record, List<VerifiedSummitItem> verifiedSummits) {
        return HikingSessionResponse.builder()
                .sessionId(record.getId())
                .userId(record.getUserId())
                .status(record.getStatus().name())
                .startedAt(record.getStartedAt())
                .endedAt(record.getEndedAt())
                .totalDistanceM(record.getTotalDistanceM())
                .totalElevationGainM(record.getTotalElevationGainM())
                .totalElevationLossM(record.getTotalElevationLossM())
                .totalDurationSec(record.getTotalDurationSec())
                .verifiedSummits(verifiedSummits == null ? Collections.emptyList() : verifiedSummits)
                .build();
    }
}