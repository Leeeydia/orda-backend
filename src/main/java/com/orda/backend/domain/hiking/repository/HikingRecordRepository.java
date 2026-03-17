package com.orda.backend.domain.hiking.repository;

import com.orda.backend.domain.hiking.entity.HikingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HikingRecordRepository extends JpaRepository<HikingRecord, Long> {
}