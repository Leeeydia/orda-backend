package com.orda.backend.domain.trail.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.service.TrailDifficultyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trails")
@RequiredArgsConstructor
public class TrailController {

    private final TrailDifficultyService trailDifficultyService;

    /**
     * 특정 구간 난이도 조회
     * GET /api/trails/difficulty?edgeId=E0001
     */
    @GetMapping("/difficulty")
    public ApiResponse<TrailDifficultyResponse> getDifficulty(
            @RequestParam String edgeId
    ) {
        TrailDifficultyResponse response = trailDifficultyService.getDifficultyByEdgeId(edgeId);
        return ApiResponse.success("난이도 조회 성공", response);
    }

    /**
     * 특정 정상 근처 전체 구간 난이도 조회
     * GET /api/trails/difficulty/summit?summitId=S0001
     */
    @GetMapping("/difficulty/summit")
    public ApiResponse<List<TrailDifficultyResponse>> getDifficultyBySummit(
            @RequestParam String summitId
    ) {
        List<TrailDifficultyResponse> responses = trailDifficultyService.getDifficultyBySummitId(summitId);
        return ApiResponse.success("정상 근처 난이도 조회 성공", responses);
    }

    /**
     * 전체 구간 난이도 조회
     * GET /api/trails/difficulty/all
     */
    @GetMapping("/difficulty/all")
    public ApiResponse<List<TrailDifficultyResponse>> getAllDifficulties() {
        List<TrailDifficultyResponse> responses = trailDifficultyService.getAllDifficulties();
        return ApiResponse.success("전체 난이도 조회 성공", responses);
    }
}