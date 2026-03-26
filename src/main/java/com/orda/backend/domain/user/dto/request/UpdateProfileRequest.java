package com.orda.backend.domain.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

// [윤종민] 개인 정보 수정 요청 — 추후 name, phone, birthDate 추가 예정
@Getter
public class UpdateProfileRequest {

    @Size(max = 50)
    private String nickname;
}