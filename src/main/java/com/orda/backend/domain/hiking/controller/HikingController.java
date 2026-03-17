package com.orda.backend.domain.hiking.controller;

import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
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
    public ResponseEntity<HikingStartResponse> startHiking(@Valid @RequestBody HikingStartRequest request) {
        HikingStartResponse response = hikingService.startHiking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{recordId}/end")
    public ResponseEntity<HikingEndResponse> endHiking(@PathVariable Long recordId) {
        HikingEndResponse response = hikingService.endHiking(recordId);
        return ResponseEntity.ok(response);
    }
}