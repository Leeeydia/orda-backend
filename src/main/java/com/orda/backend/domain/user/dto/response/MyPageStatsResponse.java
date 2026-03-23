package com.orda.backend.domain.user.dto.response;

import com.orda.backend.domain.stats.entity.UserStats;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class MyPageStatsResponse {
    private Integer totalHikes;
    private Integer totalSummits;
    private Double totalDistanceM;
    private Double totalElevationGainM;
    private Integer totalDurationSec;
    private LocalDateTime lastHikedAt;

    public static MyPageStatsResponse from(UserStats stats) {
        return MyPageStatsResponse.builder()
                .totalHikes(stats.getTotalHikes())
                .totalSummits(stats.getTotalSummits())
                .totalDistanceM(stats.getTotalDistanceM())
                .totalElevationGainM(stats.getTotalElevationGainM())
                .totalDurationSec(stats.getTotalDurationSec())
                .lastHikedAt(stats.getLastHikedAt())
                .build();
    }

    public static MyPageStatsResponse empty() {
        return MyPageStatsResponse.builder()
                .totalHikes(0)
                .totalSummits(0)
                .totalDistanceM(0.0)
                .totalElevationGainM(0.0)
                .totalDurationSec(0)
                .lastHikedAt(null)
                .build();
    }
}