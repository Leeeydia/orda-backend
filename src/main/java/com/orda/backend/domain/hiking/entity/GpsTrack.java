package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "gps_tracks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "sequence_num"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GpsTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    // ── raw GPS 원본 ──────────────────────────────────────────
    @Column(name = "raw_latitude", nullable = false)
    private Double rawLatitude;

    @Column(name = "raw_longitude", nullable = false)
    private Double rawLongitude;

    @Column(name = "raw_elevation_m")
    private Double rawElevationM;

    // ── canonical (스냅 + DEM 보정) ───────────────────────────
    @Column(name = "snapped_latitude")
    private Double snappedLatitude;

    @Column(name = "snapped_longitude")
    private Double snappedLongitude;

    @Column(name = "canonical_elevation_m")
    private Double canonicalElevationM;

    @Column(name = "elevation_source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ElevationSource elevationSource;

    // ── 보조 데이터 ───────────────────────────────────────────
    @Column(name = "accuracy_m")
    private Double accuracyM;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "geom", nullable = false, columnDefinition = "GEOMETRY(Point, 4326)")
    private Point geom;

    // ── ElevationSource ───────────────────────────────────────
    public enum ElevationSource {
        dem,
        gps_fallback,
        none
    }

    @Builder
    public GpsTrack(
            Long sessionId,
            Integer sequenceNum,
            Double rawLatitude,
            Double rawLongitude,
            Double rawElevationM,
            Double snappedLatitude,
            Double snappedLongitude,
            Double canonicalElevationM,
            ElevationSource elevationSource,
            Double accuracyM,
            LocalDateTime recordedAt,
            Point geom
    ) {
        this.sessionId = sessionId;
        this.sequenceNum = sequenceNum;
        this.rawLatitude = rawLatitude;
        this.rawLongitude = rawLongitude;
        this.rawElevationM = rawElevationM;
        this.snappedLatitude = snappedLatitude;
        this.snappedLongitude = snappedLongitude;
        this.canonicalElevationM = canonicalElevationM;
        this.elevationSource = (elevationSource != null) ? elevationSource : ElevationSource.none;
        this.accuracyM = accuracyM;
        this.recordedAt = (recordedAt != null) ? recordedAt : LocalDateTime.now();
        this.geom = geom;
    }

    // ── canonical 보정 결과 반영 ──────────────────────────────
    public void applyCanonical(
            Double snappedLatitude,
            Double snappedLongitude,
            Double canonicalElevationM,
            ElevationSource elevationSource
    ) {
        this.snappedLatitude = snappedLatitude;
        this.snappedLongitude = snappedLongitude;
        this.canonicalElevationM = canonicalElevationM;
        this.elevationSource = elevationSource;
    }
}