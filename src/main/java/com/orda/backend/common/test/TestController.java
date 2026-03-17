package com.orda.backend.common.test;

import com.orda.backend.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/response")
    public ApiResponse<String> test() {
        return ApiResponse.success("테스트 성공");
    }
}