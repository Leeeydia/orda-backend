package com.orda.backend.domain.mountain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orda.backend.domain.mountain.dto.response.Top100MountainResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MountainService {

    private final ObjectMapper objectMapper;

    public List<Top100MountainResponse> getTop100Mountains() {
        try {
            ClassPathResource resource = new ClassPathResource("data/top100mountains.json");
            InputStream inputStream = resource.getInputStream();
            return objectMapper.readValue(inputStream, new TypeReference<List<Top100MountainResponse>>() {});
        } catch (IOException e) {
            throw new RuntimeException("100대 명산 데이터 로딩 실패", e);
        }
    }
}