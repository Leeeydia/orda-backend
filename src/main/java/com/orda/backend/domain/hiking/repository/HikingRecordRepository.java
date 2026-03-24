package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.HikingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HikingRecordRepository extends JpaRepository<HikingRecord, Long> {
    List<HikingRecord> findByUserIdOrderByStartedAtDesc(Long userId);
}