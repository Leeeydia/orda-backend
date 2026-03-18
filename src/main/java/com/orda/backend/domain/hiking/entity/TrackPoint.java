package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "track_points")
@Getter
@NoArgsConstructor
public class TrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Builder
    public TrackPoint(Long sessionId, Double latitude, Double longitude, Double elevationM, LocalDateTime recordedAt) {
        this.sessionId = sessionId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationM = elevationM;
        this.recordedAt = recordedAt;
    }
}