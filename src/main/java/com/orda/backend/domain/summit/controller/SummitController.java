package com.orda.backend.domain.summit.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.summit.dto.request.SummitVerifyRequest;
import com.orda.backend.domain.summit.dto.response.SummitVerifyResponse;
import com.orda.backend.domain.summit.service.SummitService;
import com.orda.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/summit")
@RequiredArgsConstructor
public class SummitController {

    private final SummitService summitService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<SummitVerifyResponse>> verifySummit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody SummitVerifyRequest request) {
        SummitVerifyResponse response = summitService.verifySummit(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("정상 인증 완료", response));
    }
}