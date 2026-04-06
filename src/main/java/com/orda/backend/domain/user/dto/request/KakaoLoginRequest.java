// domain/user/dto/request/KakaoLoginRequest.java
package com.orda.backend.domain.user.dto.request;

public record KakaoLoginRequest(
        String code
) {}