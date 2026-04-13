package com.orda.backend.domain.mountain.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.mountain.dto.response.Top100MountainResponse;
import com.orda.backend.domain.mountain.service.MountainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mountains")
@RequiredArgsConstructor
public class MountainController {

    private final MountainService mountainService;

    @GetMapping("/top100")
    public ResponseEntity<ApiResponse<List<Top100MountainResponse>>> getTop100Mountains() {
        List<Top100MountainResponse> data = mountainService.getTop100Mountains();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}