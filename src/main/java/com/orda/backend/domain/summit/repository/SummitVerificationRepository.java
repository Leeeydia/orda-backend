package com.orda.backend.domain.summit.repository;

import com.orda.backend.domain.summit.entity.SummitVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SummitVerificationRepository extends JpaRepository<SummitVerification, Long> {

    boolean existsBySessionIdAndSummitId(Long sessionId, String summitId);
    List<SummitVerification> findAllBySessionIdOrderByVerifiedAtAsc(Long sessionId);

}