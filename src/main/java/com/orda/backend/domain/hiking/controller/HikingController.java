package com.orda.backend.domain.hiking.controller;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;
import com.orda.backend.domain.hiking.dto.response.GpsTrackSaveResponse;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingSessionResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
import com.orda.backend.domain.hiking.dto.response.ReplayResponse;
import com.orda.backend.domain.hiking.service.HikingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hiking")
@RequiredArgsConstructor
public class HikingController {

    private final HikingService hikingService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<HikingStartResponse>> startHiking(
            @Valid @RequestBody HikingStartRequest request) {
        HikingStartResponse response = hikingService.startHiking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("등산 시작 성공", response));
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<ApiResponse<HikingEndResponse>> endHiking(
            @PathVariable Long sessionId) {
        HikingEndResponse response = hikingService.endHiking(sessionId);
        return ResponseEntity.ok(ApiResponse.success("등산 종료 성공", response));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<HikingSessionResponse>> getSession(
            @PathVariable Long sessionId) {
        HikingSessionResponse response = hikingService.getSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("세션 조회 성공", response));
    }

    @PostMapping("/{sessionId}/tracks")
    public ResponseEntity<ApiResponse<GpsTrackSaveResponse>> saveGpsTrack(
            @PathVariable Long sessionId,
            @Valid @RequestBody GpsTrackRequest request) {
        GpsTrackSaveResponse response = hikingService.saveGpsTrack(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("GPS 저장 성공", response));
    }

    @GetMapping("/{sessionId}/tracks")
    public ResponseEntity<ApiResponse<GeoJsonFeatureCollectionResponse>> getTracks(
            @PathVariable Long sessionId) {
        GeoJsonFeatureCollectionResponse response = hikingService.getTracks(sessionId);
        return ResponseEntity.ok(ApiResponse.success("GPS 트랙 조회 성공", response));
    }

    @GetMapping("/{sessionId}/elevation-profile")
    public ResponseEntity<ApiResponse<ElevationProfileResponse>> getElevationProfile(
            @PathVariable Long sessionId) {
        ElevationProfileResponse response = hikingService.getElevationProfile(sessionId);
        return ResponseEntity.ok(ApiResponse.success("고도 프로파일 조회 성공", response));
    }

    @GetMapping("/{sessionId}/replay")
    public ResponseEntity<ApiResponse<ReplayResponse>> getReplay(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Integer maxPoints,
            @RequestParam(required = false) Integer targetDurationSeconds) {
        ReplayResponse response = hikingService.getReplay(sessionId, maxPoints, targetDurationSeconds);
        return ResponseEntity.ok(ApiResponse.success("3D 리플레이 조회 성공", response));
    }
}