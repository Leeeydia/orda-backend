package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "gps_tracks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpsTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private HikingRecord hikingRecord;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "accuracy_m")
    private Double accuracyM;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "geom", nullable = false, columnDefinition = "GEOMETRY(Point, 4326)")
    private Point geom;
}