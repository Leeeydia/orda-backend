package com.orda.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.static-resource-dir}")  // 기존 upload-dir → static-resource-dir로 변경
    private String staticResourceDir;

    // 서버 로컬에 저장된 이미지를 http://localhost:8080/uploads/** 경로로 접근 가능하게 설정
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path resourcePath = Paths.get(staticResourceDir).toAbsolutePath();  // getParent() 제거
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + resourcePath + "/");
    }
}