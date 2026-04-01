package com.orda.backend.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

// 개인 정보 수정 요청 — 닉네임, 전화번호만 수정 가능 (이름, 생년월일은 고정)
@Getter
public class UpdateProfileRequest {

    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다")
    private String nickname;

    @Pattern(regexp = "^010\\d{8}$", message = "전화번호 형식이 올바르지 않습니다 (예: 01012345678)")
    private String phone;
}