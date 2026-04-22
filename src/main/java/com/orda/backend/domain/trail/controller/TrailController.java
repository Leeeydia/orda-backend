package com.orda.backend.domain.trail.controller;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.trail.dto.request.EdgeIdsRequest;
import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.dto.response.TrailNearbyResponse;
import com.orda.backend.domain.trail.service.TrailDifficultyService;
import com.orda.backend.domain.trail.service.TrailNearbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trails")
@RequiredArgsConstructor
public class TrailController {

    private final TrailDifficultyService trailDifficultyService;
    private final TrailNearbyService trailNearbyService;

    // bbox 파라미터 유효성 검증 한국 범위 상수
    private static final double KR_MIN_LNG = 120.0;
    private static final double KR_MAX_LNG = 140.0;
    private static final double KR_MIN_LAT = 30.0;
    private static final double KR_MAX_LAT = 45.0;

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

        // bbox 파라미터 유효성 검증 추가
        if (minLng >= maxLng || minLat >= maxLat) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("잘못된 bbox 파라미터: min 값이 max 값보다 크거나 같습니다."));
        }
        if (minLng < KR_MIN_LNG || maxLng > KR_MAX_LNG || minLat < KR_MIN_LAT || maxLat > KR_MAX_LAT) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("잘못된 bbox 파라미터: 한국 범위(lng: 120~140, lat: 30~45)를 벗어났습니다."));
        }

        return ResponseEntity.ok(
                ApiResponse.success("뷰포트 기반 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapByBbox(minLng, minLat, maxLng, maxLat)));
    }

    // edgeIds 목록 기반 난이도 지도 조회 엔드포인트 추가 (GET - 산 단건 클릭용)
    @GetMapping("/difficulty/map/edges")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMapByEdgeIds(
            @RequestParam List<String> edgeIds) {
        if (edgeIds == null || edgeIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("edgeIds가 비어있습니다."));
        }
        return ResponseEntity.ok(
                ApiResponse.success("edgeIds 기반 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapByEdgeIds(edgeIds)));
    }

    // edgeIds 목록 기반 난이도 지도 조회 엔드포인트 추가 (POST - 명산 전체 모드용, URL 길이 제한 우회)
    @PostMapping("/difficulty/map/edges")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMapByEdgeIdsPost(
            @RequestBody EdgeIdsRequest request) {
        if (request.edgeIds() == null || request.edgeIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("edgeIds가 비어있습니다."));
        }
        return ResponseEntity.ok(
                ApiResponse.success("edgeIds 기반 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapByEdgeIds(request.edgeIds())));
    }

    @GetMapping("/check-nearby")
    public ResponseEntity<ApiResponse<TrailNearbyResponse>> checkNearby(
            @RequestParam double lat,
            @RequestParam double lng) {
        TrailNearbyResponse response = trailNearbyService.checkNearby(lat, lng);
        return ResponseEntity.ok(ApiResponse.success("등산로 근접 확인 성공", response));
    }
}