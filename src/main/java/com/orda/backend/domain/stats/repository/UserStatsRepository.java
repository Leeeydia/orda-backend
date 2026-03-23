package com.orda.backend.domain.stats.repository;

import com.orda.backend.domain.stats.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {
    Optional<UserStats> findByUserId(Long userId);
}