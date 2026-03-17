package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hiking_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HikingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HikingStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public HikingRecord(Long userId, LocalDateTime startedAt) {
        this.userId = userId;
        this.startedAt = startedAt;
        this.status = HikingStatus.IN_PROGRESS;
    }

    public void end(LocalDateTime endedAt) {
        if (this.status == HikingStatus.COMPLETED) {
            throw new IllegalStateException("이미 종료된 등산 기록입니다.");
        }
        this.status = HikingStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}