package com.orda.backend.domain.summit.controller;

import com.orda.backend.domain.summit.dto.request.SummitVerifyRequest;
import com.orda.backend.domain.summit.dto.response.SummitVerifyResponse;
import com.orda.backend.domain.summit.service.SummitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/summit")
@RequiredArgsConstructor
public class SummitController {

    private final SummitService summitService;

    @PostMapping("/verify")
    public ResponseEntity<SummitVerifyResponse> verifySummit(@RequestBody SummitVerifyRequest request) {
        return ResponseEntity.ok(summitService.verifySummit(request));
    }
}