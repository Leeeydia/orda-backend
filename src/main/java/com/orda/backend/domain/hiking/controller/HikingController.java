package com.orda.backend.domain.hiking.controller;

import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.GpsTrackResponse;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingSessionResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
import com.orda.backend.domain.hiking.service.HikingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hiking")
@RequiredArgsConstructor
public class HikingController {

    private final HikingService hikingService;

    @PostMapping("/start")
    public ResponseEntity<HikingStartResponse> startHiking(@Valid @RequestBody HikingStartRequest request) {
        HikingStartResponse response = hikingService.startHiking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<HikingEndResponse> endHiking(@PathVariable Long sessionId) {
        HikingEndResponse response = hikingService.endHiking(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<HikingSessionResponse> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(hikingService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/tracks")
    public ResponseEntity<Void> saveGpsTrack(
            @PathVariable Long sessionId,
            @RequestBody GpsTrackRequest request) {
        hikingService.saveGpsTrack(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{sessionId}/tracks")
    public ResponseEntity<List<GpsTrackResponse>> getTracks(@PathVariable Long sessionId) {
        return ResponseEntity.ok(hikingService.getTracks(sessionId));
    }
}