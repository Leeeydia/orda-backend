package com.orda.backend.domain.test.controller;

import com.orda.backend.common.response.ApiResponse;
import com.orda.backend.domain.test.dto.request.TestRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/response")
    public ApiResponse<String> test() {
        return ApiResponse.success("테스트 성공");
    }

    @GetMapping("/error")
    public ApiResponse<String> error() {
        throw new IllegalArgumentException("잘못된 요청입니다.");
    }

    @PostMapping("/validation")
    public ApiResponse<String> validation(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success("검증 성공");
    }
}