package com.orda.backend.domain.trail.service;

import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.entity.TrailEdge;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrailDifficultyService {

    private final TrailEdgeRepository trailEdgeRepository;

    // 기능정의서 No.23 원본:
    // slope 0.35 + elevation 0.25 + distance 0.15 + terrain 0.15 + user_time 0.10
    // 현재는 user_time_factor 데이터가 없으므로 4변수로 운영
    private static final double WEIGHT_SLOPE = 0.40;
    private static final double WEIGHT_ELEVATION = 0.25;
    private static final double WEIGHT_DISTANCE = 0.15;
    private static final double WEIGHT_TERRAIN = 0.20;

    private static final double MAX_SLOPE = 35.0;
    private static final double MAX_ELEVATION_DIFF = 500.0;
    private static final double MAX_DISTANCE = 5000.0;

    // null 또는 매핑되지 않는 값은 '보통' 기본값
    private static final double DEFAULT_TERRAIN_SCORE = 60.0;

    // 실데이터 기준 surface 매핑
    private static final Map<String, Double> SURFACE_SCORE_MAP = Map.ofEntries(
            // 매우 쉬움 (20)
            Map.entry("asphalt", 20.0),
            Map.entry("concrete", 20.0),
            Map.entry("paved", 20.0),
            Map.entry("paving_stones", 20.0),
            Map.entry("paving_stone", 20.0),

            // 쉬움 (40)
            Map.entry("compacted", 40.0),
            Map.entry("fine_gravel", 40.0),
            Map.entry("ground", 40.0),
            Map.entry("wood", 40.0),
            Map.entry("woodchips", 40.0),

            // 보통 (60)
            Map.entry("dirt", 60.0),
            Map.entry("earth", 60.0),
            Map.entry("sand", 60.0),
            Map.entry("gravel", 60.0),
            Map.entry("흙길", 60.0),
            Map.entry("토사", 60.0),

            // 어려움 (80)
            Map.entry("unpaved", 80.0),
            Map.entry("cobblestone", 80.0),
            Map.entry("pebblestone", 80.0),
            Map.entry("mud", 80.0),

            // 매우 어려움 (100)
            Map.entry("rock", 100.0),
            Map.entry("토사/암반", 100.0)
    );

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
        double terrainScore = convertSurfaceToScore(edge.getSurface());

        double normalizedSlope = normalize(Math.abs(slope), MAX_SLOPE);
        double normalizedElev = normalize(Math.abs(elevDiff), MAX_ELEVATION_DIFF);
        double normalizedDist = normalize(distance, MAX_DISTANCE);

        double score = WEIGHT_SLOPE * normalizedSlope
                + WEIGHT_ELEVATION * normalizedElev
                + WEIGHT_DISTANCE * normalizedDist
                + WEIGHT_TERRAIN * terrainScore;

        return Math.round(score * 10.0) / 10.0;
    }

    private double convertSurfaceToScore(String surface) {
        if (surface == null) {
            return DEFAULT_TERRAIN_SCORE;
        }

        String normalizedSurface = surface.trim().toLowerCase();
        return SURFACE_SCORE_MAP.getOrDefault(normalizedSurface, DEFAULT_TERRAIN_SCORE);
    }

    private double normalize(double value, double max) {
        if (max <= 0) {
            return 0.0;
        }
        return Math.min((value / max) * 100.0, 100.0);
    }

    private String classifyGrade(double score) {
        if (score <= 20) {
            return "easy";
        }
        if (score <= 40) {
            return "moderate";
        }
        if (score <= 60) {
            return "hard";
        }
        if (score <= 80) {
            return "very_hard";
        }
        return "extreme";
    }
}