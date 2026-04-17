package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GpsTrackRepository extends JpaRepository<GpsTrack, Long> {

    Optional<GpsTrack> findBySessionIdAndSequenceNum(
            @Param("sessionId") Long sessionId,
            @Param("sequenceNum") int sequenceNum);

    @Query("SELECT g FROM GpsTrack g WHERE g.sessionId = :sessionId ORDER BY g.sequenceNum ASC")
    List<GpsTrack> findBySessionIdOrderBySequenceNum(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(g) FROM GpsTrack g WHERE g.sessionId = :sessionId")
    int countBySessionId(@Param("sessionId") Long sessionId);
}