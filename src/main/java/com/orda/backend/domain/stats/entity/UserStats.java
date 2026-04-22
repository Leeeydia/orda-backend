package com.orda.backend.domain.stats.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "total_hikes", nullable = false)
    private Integer totalHikes = 0;

    @Column(name = "total_summits", nullable = false)
    private Integer totalSummits = 0;

    @Column(name = "total_distance_m", nullable = false)
    private Double totalDistanceM = 0.0;

    @Column(name = "total_elevation_gain_m", nullable = false)
    private Double totalElevationGainM = 0.0;

    @Column(name = "total_duration_sec", nullable = false)
    private Integer totalDurationSec = 0;

    @Column(name = "last_hiked_at")
    private LocalDateTime lastHikedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static UserStats createForUser(Long userId) {
        UserStats stats = new UserStats();
        stats.userId = userId;
        stats.totalHikes = 0;
        stats.totalSummits = 0;
        stats.totalDistanceM = 0.0;
        stats.totalElevationGainM = 0.0;
        stats.totalDurationSec = 0;
        return stats;
    }

    public void addHiking(Double distanceM, Double elevationGainM,
                          Integer durationSec, LocalDateTime hikedAt) {
        this.totalHikes += 1;
        this.totalDistanceM += distanceM != null ? distanceM : 0.0;
        this.totalElevationGainM += elevationGainM != null ? elevationGainM : 0.0;
        this.totalDurationSec += durationSec != null ? durationSec : 0;
        this.lastHikedAt = hikedAt;
    }

    // 정상 인증 시 총 정상 수 1 증가
    public void incrementSummits() {
        this.totalSummits += 1;
    }
}