package com.orda.backend.domain.test.dto.request;

import jakarta.validation.constraints.NotBlank;

public class TestRequest {

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}