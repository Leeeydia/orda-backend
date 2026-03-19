package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GpsTrackRepository extends JpaRepository<GpsTrack, Long> {

    @Query("SELECT COALESCE(MAX(g.sequenceNum), 0) FROM GpsTrack g WHERE g.hikingRecord.id = :sessionId")
    int findMaxSequenceNum(@Param("sessionId") Long sessionId);

    @Query("SELECT g FROM GpsTrack g WHERE g.hikingRecord.id = :sessionId ORDER BY g.sequenceNum ASC")
    List<GpsTrack> findBySessionIdOrderBySequenceNum(@Param("sessionId") Long sessionId);
}