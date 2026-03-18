package com.orda.backend.domain.summit.repository;

import com.orda.backend.domain.summit.entity.SummitVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummitVerificationRepository extends JpaRepository<SummitVerification, Long> {
}