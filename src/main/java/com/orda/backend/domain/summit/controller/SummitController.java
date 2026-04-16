package com.orda.backend.domain.summit.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.summit.dto.request.SummitVerifyRequest;
import com.orda.backend.domain.summit.dto.response.SummitVerifyResponse;
import com.orda.backend.domain.summit.service.SummitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/summit")
@RequiredArgsConstructor
public class SummitController {

    private final SummitService summitService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<SummitVerifyResponse>> verifySummit(
            @RequestBody SummitVerifyRequest request) {
        SummitVerifyResponse response = summitService.verifySummit(request);
        return ResponseEntity.ok(ApiResponse.success("정상 인증 완료", response));
    }

    @PostMapping("/verify/photo")
    public ResponseEntity<ApiResponse<SummitVerifyResponse>> verifySummitWithPhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude
    ) {
        SummitVerifyResponse response = summitService.verifySummitWithPhoto(
                sessionId, latitude, longitude, photo
        );
        return ResponseEntity.ok(ApiResponse.success("사진 인증 처리 완료", response));
    }
}