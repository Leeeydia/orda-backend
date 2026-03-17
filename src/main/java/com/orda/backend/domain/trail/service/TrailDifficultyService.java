package com.orda.backend.domain.trail.service;

import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.entity.TrailEdge;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrailDifficultyService {

    private final TrailEdgeRepository trailEdgeRepository;

    private static final double WEIGHT_SLOPE = 0.45;
    private static final double WEIGHT_ELEVATION = 0.30;
    private static final double WEIGHT_DISTANCE = 0.25;

    private static final double MAX_SLOPE = 35.0;
    private static final double MAX_ELEVATION_DIFF = 500.0;
    private static final double MAX_DISTANCE = 5000.0;

    public TrailDifficultyResponse getDifficultyByEdgeId(String edgeId) {
        TrailEdge edge = findEdgeById(edgeId);

        double score = calculateDifficultyScore(edge);
        String grade = classifyGrade(score);

        return TrailDifficultyResponse.of(edge, score, grade);
    }

    public List<TrailDifficultyResponse> getDifficultyBySummitId(String summitId) {
        List<TrailEdge> edges = trailEdgeRepository.findByNearestSummitId(summitId);

        return edges.stream()
                .map(edge -> {
                    double score = calculateDifficultyScore(edge);
                    String grade = classifyGrade(score);
                    return TrailDifficultyResponse.of(edge, score, grade);
                })
                .collect(Collectors.toList());
    }

    public List<TrailDifficultyResponse> getAllDifficulties() {
        List<TrailEdge> edges = trailEdgeRepository.findAll();

        return edges.stream()
                .map(edge -> {
                    double score = calculateDifficultyScore(edge);
                    String grade = classifyGrade(score);
                    return TrailDifficultyResponse.of(edge, score, grade);
                })
                .collect(Collectors.toList());
    }

    private TrailEdge findEdgeById(String edgeId) {
        return trailEdgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 구간입니다: " + edgeId));
    }

    private double calculateDifficultyScore(TrailEdge edge) {
        double slope = edge.getSlopePercent() != null ? edge.getSlopePercent() : 0.0;
        double elevDiff = edge.getElevationDiffM() != null ? edge.getElevationDiffM() : 0.0;
        double distance = edge.getDistanceM() != null ? edge.getDistanceM() : 0.0;

        double normalizedSlope = normalize(Math.abs(slope), MAX_SLOPE);
        double normalizedElev = normalize(Math.abs(elevDiff), MAX_ELEVATION_DIFF);
        double normalizedDist = normalize(distance, MAX_DISTANCE);

        double score = WEIGHT_SLOPE * normalizedSlope
                + WEIGHT_ELEVATION * normalizedElev
                + WEIGHT_DISTANCE * normalizedDist;

        return Math.round(score * 10.0) / 10.0;
    }

    private double normalize(double value, double max) {
        if (max <= 0) return 0.0;
        return Math.min((value / max) * 100.0, 100.0);
    }

    private String classifyGrade(double score) {
        if (score <= 20) return "easy";
        if (score <= 40) return "moderate";
        if (score <= 60) return "hard";
        if (score <= 80) return "very_hard";
        return "extreme";
    }
}