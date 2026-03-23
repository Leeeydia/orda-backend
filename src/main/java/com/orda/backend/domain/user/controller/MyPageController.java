package com.orda.backend.domain.user.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.user.dto.request.ChangePasswordRequest;
import com.orda.backend.domain.user.dto.request.UpdateProfileRequest;
import com.orda.backend.domain.user.dto.response.MyPageHikingRecordResponse;
import com.orda.backend.domain.user.dto.response.MyPageProfileResponse;
import com.orda.backend.domain.user.dto.response.MyPageStatsResponse;
import com.orda.backend.domain.user.service.MyPageService;
import com.orda.backend.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<MyPageProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MyPageProfileResponse response = myPageService.getProfile(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MyPageStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MyPageStatsResponse response = myPageService.getStats(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("통계 조회 성공", response));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<MyPageProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        MyPageProfileResponse response = myPageService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공", response));
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<MyPageHikingRecordResponse>>> getHikingRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<MyPageHikingRecordResponse> response = myPageService.getHikingRecords(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("등산 기록 조회 성공", response));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        myPageService.changePassword(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", null));
    }
}