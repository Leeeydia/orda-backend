package com.orda.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

// [윤종민] 비밀번호 변경 요청
@Getter
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    private String newPassword;
}