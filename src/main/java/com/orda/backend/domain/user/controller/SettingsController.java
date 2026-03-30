package com.orda.backend.domain.user.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.user.dto.request.ChangePasswordRequest;
import com.orda.backend.domain.user.dto.request.UpdateProfileRequest;
import com.orda.backend.domain.user.dto.response.SettingsProfileResponse;
import com.orda.backend.domain.user.service.SettingsService;
import com.orda.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 개인 정보 수정 API — 마이페이지에서 분리
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    // 수정 페이지용 프로필 조회
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<SettingsProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SettingsProfileResponse response = settingsService.getProfile(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", response));
    }

    // 프로필 수정 — 닉네임, 전화번호 수정 가능 (이름, 생년월일은 고정)
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<SettingsProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        SettingsProfileResponse response = settingsService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공", response));
    }

    // 비밀번호 변경
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        settingsService.changePassword(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", null));
    }
}
