package com.orda.backend.domain.trail.repository;

import com.orda.backend.domain.trail.entity.TrailEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrailEdgeRepository extends JpaRepository<TrailEdge, String> {

    List<TrailEdge> findByNearestSummitId(String nearestSummitId);
}