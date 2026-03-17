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

    // 난이도 가중치 (기능정의서 No.23 기반, 초기 버전)
    private static final double WEIGHT_SLOPE = 0.45;
    private static final double WEIGHT_ELEVATION = 0.30;
    private static final double WEIGHT_DISTANCE = 0.25;

    // 정규화 기준값
    private static final double MAX_SLOPE = 35.0;
    private static final double MAX_ELEVATION_DIFF = 500.0;
    private static final double MAX_DISTANCE = 5000.0;

    /**
     * 특정 구간 하나의 난이도를 계산하여 반환한다.
     */
    public TrailDifficultyResponse getDifficultyByEdgeId(String edgeId) {
        TrailEdge edge = trailEdgeRepository.findByEdgeId(edgeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 구간입니다: " + edgeId));

        double score = calculateDifficultyScore(edge);
        String grade = classifyGrade(score);

        return TrailDifficultyResponse.of(edge, score, grade);
    }

    /**
     * 특정 정상 근처 모든 구간의 난이도를 계산하여 반환한다.
     */
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

    /**
     * 전체 구간의 난이도를 계산하여 반환한다.
     */
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

    /**
     * 난이도 점수 계산 (0~100)
     *
     * 기능정의서 No.23 원본:
     *   0.35×slope + 0.25×elevation + 0.15×distance + 0.15×terrain + 0.10×user_time
     *
     * 초기 버전 (terrain_roughness, user_time_factor 데이터 미확보):
     *   0.45×slope + 0.30×elevation + 0.25×distance
     *
     * 학술 근거:
     *   - 서은수, 최세휴(2012) "GIS를 활용한 등산로 경사도 분석 및 등급책정"
     *   - 이혜숙 외(2009) "휴대용 GPS에 의한 등산로 경사분석"
     *   → 경사도가 등산로 난이도의 가장 중요한 변수임을 검증
     */
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

    /**
     * 0~100 범위로 정규화한다.
     * 기준값 이상이면 100으로 고정된다.
     */
    private double normalize(double value, double max) {
        if (max <= 0) return 0.0;
        return Math.min((value / max) * 100.0, 100.0);
    }

    /**
     * 점수를 5단계 등급으로 분류한다.
     * 기능정의서 No.24 색상 기준: 녹 → 노 → 주 → 빨 → 검
     */
    private String classifyGrade(double score) {
        if (score <= 20) return "easy";
        if (score <= 40) return "moderate";
        if (score <= 60) return "hard";
        if (score <= 80) return "very_hard";
        return "extreme";
    }
}