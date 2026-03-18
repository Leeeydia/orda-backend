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

    // ──────────────────────────────────────────────
    // 난이도 가중치 (4변수 고정)
    // 기능정의서 No.23 원본: slope 0.35 + elevation 0.25 + distance 0.15 + terrain 0.15 + user_time 0.10
    // 현재: user_time_factor 데이터 미확보로 4변수 운영
    // user_time_factor 확보 시 5변수로 확장 예정
    // ──────────────────────────────────────────────
    private static final double WEIGHT_SLOPE = 0.40;
    private static final double WEIGHT_ELEVATION = 0.25;
    private static final double WEIGHT_DISTANCE = 0.15;
    private static final double WEIGHT_TERRAIN = 0.20;

    // 정규화 기준값
    private static final double MAX_SLOPE = 35.0;
    private static final double MAX_ELEVATION_DIFF = 500.0;
    private static final double MAX_DISTANCE = 5000.0;

    // ──────────────────────────────────────────────
    // surface → terrain_roughness 점수 변환
    // 근거: 국립공원관리공단 탐방로 등급제(2013)
    //   - GPS 측량으로 1,700km 탐방로의 노면 상태를 조사
    //   - 매우쉬움/쉬움/보통/어려움/매우어려움 5단계 분류
    //   - 0~100 등간격(20점 간격) 변환 적용
    //   - null일 때 60점(보통): 전체 탐방로의 69%가 보통 등급
    // ──────────────────────────────────────────────
    private static final double DEFAULT_TERRAIN_SCORE = 60.0;

    private static final Map<String, Double> SURFACE_SCORE_MAP = Map.ofEntries(
            // 매우 쉬움 (20점) — 포장길, 휠체어 가능
            Map.entry("asphalt", 20.0),
            Map.entry("concrete", 20.0),
            Map.entry("paved", 20.0),
            // 쉬움 (40점) — 평탄한 흙길, 운동화 가능
            Map.entry("compacted", 40.0),
            Map.entry("fine_gravel", 40.0),
            Map.entry("ground", 40.0),
            // 보통 (60점) — 일반 흙 노면, 등산화 필요
            Map.entry("dirt", 60.0),
            Map.entry("earth", 60.0),
            Map.entry("sand", 60.0),
            Map.entry("gravel", 60.0),
            Map.entry("woodchips", 60.0),
            // 어려움 (80점) — 거친 노면
            Map.entry("cobblestone", 80.0),
            Map.entry("pebblestone", 80.0),
            Map.entry("mud", 80.0),
            Map.entry("unpaved", 80.0),
            // 매우 어려움 (100점) — 암반
            Map.entry("rock", 100.0)
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

    /**
     * 난이도 점수 계산 (0~100)
     *
     * 항상 4변수 공식을 사용한다.
     * surface가 null이면 기본값 60점(보통)을 적용한다.
     * 나중에 user_time_factor 데이터가 확보되면 5변수로 확장 예정.
     */
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

    /**
     * surface 문자열을 terrain_roughness 점수로 변환한다.
     *
     * 근거: 국립공원관리공단 탐방로 등급제(2013)
     * 5단계(매우쉬움~매우어려움)를 0~100 등간격 변환
     * null 또는 매핑되지 않는 값은 '보통(60점)'으로 처리
     */
    private double convertSurfaceToScore(String surface) {
        if (surface == null) {
            return DEFAULT_TERRAIN_SCORE;
        }
        return SURFACE_SCORE_MAP.getOrDefault(surface.toLowerCase(), DEFAULT_TERRAIN_SCORE);
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