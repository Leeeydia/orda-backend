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

    // bbox 파라미터 유효성 검증 한국 범위 상수
    private static final double KR_MIN_LNG = 120.0;
    private static final double KR_MAX_LNG = 140.0;
    private static final double KR_MIN_LAT = 30.0;
    private static final double KR_MAX_LAT = 45.0;
    private static final double MAX_RADIUS_KM = 20.0;

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

    // 산 좌표 기반 반경 난이도 지도 조회 엔드포인트 추가
    @GetMapping("/difficulty/map/mountain")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getDifficultyMapByMountain(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm) {

        if (lat < KR_MIN_LAT || lat > KR_MAX_LAT || lng < KR_MIN_LNG || lng > KR_MAX_LNG) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("잘못된 좌표: 한국 범위를 벗어났습니다."));
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("잘못된 반경: 0 초과 " + MAX_RADIUS_KM + "km 이하여야 합니다."));
        }

        return ResponseEntity.ok(
                ApiResponse.success("산 기준 난이도 지도 조회 성공",
                        trailDifficultyService.getDifficultyMapByMountain(lat, lng, radiusKm)));
    }

    // edgeIds 목록 기반 난이도 지도 조회 엔드포인트 추가
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
}