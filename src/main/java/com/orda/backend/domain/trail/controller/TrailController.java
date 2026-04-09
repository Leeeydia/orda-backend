package com.orda.backend.domain.trail.controller;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.service.TrailDifficultyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trails")
@RequiredArgsConstructor
public class TrailController {

    private final TrailDifficultyService trailDifficultyService;

    @GetMapping("/difficulty")
    public ResponseEntity<ApiResponse<TrailDifficultyResponse>> getDifficulty(
            @RequestParam String edgeId) {
        return ResponseEntity.ok(
                ApiResponse.success("난이도 조회 성공", trailDifficultyService.getByEdgeId(edgeId)));
    }

    @GetMapping("/difficulty/summit")
    public ResponseEntity<ApiResponse<List<TrailDifficultyResponse>>> getDifficultySummit(
            @RequestParam String summitId) {
        return ResponseEntity.ok(
                ApiResponse.success("정상 기준 난이도 조회 성공", trailDifficultyService.getBySummitId(summitId)));
    }

    @GetMapping("/difficulty/all")
    public ResponseEntity<ApiResponse<List<TrailDifficultyResponse>>> getAllDifficulty() {
        return ResponseEntity.ok(
                ApiResponse.success("전체 난이도 조회 성공", trailDifficultyService.getAll()));
    }

    @GetMapping("/difficulty/map")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMap() {
        return ResponseEntity.ok(
                ApiResponse.success("난이도 지도 조회 성공", trailDifficultyService.getDifficultyMap()));
    }

    @GetMapping("/difficulty/map/summit")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMapBySummit(
            @RequestParam String summitId) {
        return ResponseEntity.ok(
                ApiResponse.success("정상 기준 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapBySummit(summitId)));
    }

    // bbox 기반 난이도 지도 조회 엔드포인트 추가
    @GetMapping("/difficulty/map/bbox")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMapByBbox(
            @RequestParam double minLng,
            @RequestParam double minLat,
            @RequestParam double maxLng,
            @RequestParam double maxLat) {
        return ResponseEntity.ok(
                ApiResponse.success("뷰포트 기반 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapByBbox(minLng, minLat, maxLng, maxLat)));
    }
}