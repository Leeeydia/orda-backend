package com.orda.backend.domain.mountain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orda.backend.domain.mountain.dto.response.Top100MountainResponse;
import jakarta.annotation.PostConstruct;
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
    private List<Top100MountainResponse> cachedMountains;

    @PostConstruct
    public void init() {
        ClassPathResource resource = new ClassPathResource("data/top100mountains.json");
        try (InputStream is = resource.getInputStream()) {
            cachedMountains = List.copyOf(
                    objectMapper.readValue(is, new TypeReference<>() {})
            );
        } catch (IOException e) {
            throw new IllegalStateException("100대 명산 데이터 로딩 실패", e);
        }
    }

    public List<Top100MountainResponse> getTop100Mountains() {
        return cachedMountains;
    }
}