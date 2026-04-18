package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GpsTrackRepository extends JpaRepository<GpsTrack, Long> {

    Optional<GpsTrack> findBySessionIdAndSequenceNum(
            @Param("sessionId") Long sessionId,
            @Param("sequenceNum") int sequenceNum);

    @Modifying
    @Query(value = """
            INSERT INTO gps_tracks (
                session_id, sequence_num,
                raw_latitude, raw_longitude, raw_elevation_m,
                snapped_latitude, snapped_longitude,
                canonical_elevation_m, elevation_source,
                accuracy_m, recorded_at, geom
            ) VALUES (
                :sessionId, :sequenceNum,
                :rawLat, :rawLng, :rawElevM,
                :snappedLat, :snappedLng,
                :canonicalElevM, :elevSource,
                :accuracyM, :recordedAt,
                ST_SetSRID(ST_MakePoint(:snappedLng, :snappedLat), 4326)
            )
            ON CONFLICT (session_id, sequence_num) DO NOTHING
            """, nativeQuery = true)
    int insertOnConflictDoNothing(
            @Param("sessionId") Long sessionId,
            @Param("sequenceNum") int sequenceNum,
            @Param("rawLat") double rawLat,
            @Param("rawLng") double rawLng,
            @Param("rawElevM") Double rawElevM,
            @Param("snappedLat") double snappedLat,
            @Param("snappedLng") double snappedLng,
            @Param("canonicalElevM") Double canonicalElevM,
            @Param("elevSource") String elevSource,
            @Param("accuracyM") Double accuracyM,
            @Param("recordedAt") LocalDateTime recordedAt);

    @Query("SELECT g FROM GpsTrack g WHERE g.sessionId = :sessionId ORDER BY g.sequenceNum ASC")
    List<GpsTrack> findBySessionIdOrderBySequenceNum(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(g) FROM GpsTrack g WHERE g.sessionId = :sessionId")
    int countBySessionId(@Param("sessionId") Long sessionId);
}