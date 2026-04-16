package com.orda.backend.domain.summit.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenAiVisionService {

    private final WebClient webClient;
    private final String model;

    public OpenAiVisionService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 사진에서 정상석 텍스트를 인식한다.
     * 반환: {"summitName": "북한산", "elevation": "836.5m", "recognized": true, "reason": "..."}
     */
    public Map<String, Object> analyzeSummitPhoto(MultipartFile photo) {
        try {
            String base64Image = resizeAndEncode(photo);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 300,
                    "messages", List.of(
                            Map.of("role", "system", "content", buildSystemPrompt()),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "image_url", "image_url",
                                            Map.of("url", "data:image/jpeg;base64," + base64Image,
                                                    "detail", "low")),
                                    Map.of("type", "text", "text",
                                            "이 사진을 분석해주세요.")
                            ))
                    )
            );

            String responseBody = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseBody);

        } catch (Exception e) {
            log.error("OpenAI Vision API 호출 실패", e);
            return Map.of(
                    "recognized", false,
                    "summitName", "",
                    "elevation", "",
                    "reason", "AI 분석 실패: " + e.getMessage()
            );
        }
    }

    private String buildSystemPrompt() {
        return """
                당신은 등산 정상석/정상 표지판 사진을 분석하는 전문가입니다.
                사진에서 아래 정보를 추출하세요:
                1. 산 이름 (정상석에 적힌 산 이름)
                2. 해발 고도 (표지판에 적힌 높이)
                
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.
                {
                  "recognized": true 또는 false,
                  "summitName": "산 이름 (인식 못하면 빈 문자열)",
                  "elevation": "해발 고도 (인식 못하면 빈 문자열)",
                  "reason": "판단 근거 한 줄 설명"
                }
                
                정상석이나 정상 표지판이 아닌 사진이면 recognized를 false로 하세요.
                """;
    }

    /**
     * 이미지를 512px로 리사이즈 후 base64 인코딩 (비용 절감)
     */
    private String resizeAndEncode(MultipartFile photo) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(photo.getInputStream())
                .size(512, 512)
                .outputFormat("jpeg")
                .outputQuality(0.8)
                .toOutputStream(outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String responseBody) {
        try {
            // Jackson으로 파싱
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> response = mapper.readValue(responseBody, Map.class);

            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            // JSON 블록 추출 (```json ... ``` 감싸져 있을 수 있음)
            String json = content;
            if (content.contains("```")) {
                json = content.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .trim();
            }

            return mapper.readValue(json, Map.class);

        } catch (Exception e) {
            log.error("OpenAI 응답 파싱 실패: {}", responseBody, e);
            return Map.of(
                    "recognized", false,
                    "summitName", "",
                    "elevation", "",
                    "reason", "응답 파싱 실패"
            );
        }
    }
}