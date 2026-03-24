package com.orda.backend.domain.user.dto.response;

import com.orda.backend.domain.hiking.entity.HikingRecord;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class MyPageHikingRecordResponse {
    private Long sessionId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Double totalDistanceM;
    private Double totalElevationGainM;
    private Integer totalDurationSec;

    public static MyPageHikingRecordResponse from(HikingRecord record) {
        return MyPageHikingRecordResponse.builder()
                .sessionId(record.getId())
                .status(record.getStatus().name())
                .startedAt(record.getStartedAt())
                .endedAt(record.getEndedAt())
                .totalDistanceM(record.getTotalDistanceM())
                .totalElevationGainM(record.getTotalElevationGainM())
                .totalDurationSec(record.getTotalDurationSec())
                .build();
    }
}