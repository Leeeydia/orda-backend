package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hiking_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HikingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HikingStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "total_distance_m")
    private Double totalDistanceM;

    @Column(name = "total_elevation_gain_m")
    private Double totalElevationGainM;

    @Column(name = "total_elevation_loss_m")
    private Double totalElevationLossM;

    @Column(name = "total_duration_sec")
    private Integer totalDurationSec;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public HikingRecord(Long userId, LocalDateTime startedAt) {
        this.userId = userId;
        this.startedAt = startedAt;
        this.status = HikingStatus.ACTIVE;
    }

    public void complete(LocalDateTime endedAt) {
        validateNotFinished();
        this.status = HikingStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    public void pause() {
        if (this.status != HikingStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 세션만 일시정지할 수 있습니다.");
        }
        this.status = HikingStatus.PAUSED;
    }

    public void resume() {
        if (this.status != HikingStatus.PAUSED) {
            throw new IllegalStateException("일시정지 상태에서만 재개할 수 있습니다.");
        }
        this.status = HikingStatus.ACTIVE;
    }

    public void abandon(LocalDateTime endedAt) {
        validateNotFinished();
        this.status = HikingStatus.ABANDONED;
        this.endedAt = endedAt;
    }

    // [윤종민] 등산 종료 시 GPS 트랙 집계 결과를 세션에 저장하기 위해 추가
    public void updateStats(Double totalDistanceM, Double totalElevationGainM,
                            Double totalElevationLossM, Integer totalDurationSec) {
        this.totalDistanceM = totalDistanceM;
        this.totalElevationGainM = totalElevationGainM;
        this.totalElevationLossM = totalElevationLossM;
        this.totalDurationSec = totalDurationSec;
    }

    private void validateNotFinished() {
        if (this.status == HikingStatus.COMPLETED || this.status == HikingStatus.ABANDONED) {
            throw new IllegalStateException("이미 종료된 등산 세션입니다. 현재 상태: " + this.status);
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}