package com.orda.backend.domain.user.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class SignupRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
            message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 합니다"
    )
    private String password;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다")
    @Pattern(
            regexp = "^[a-zA-Z0-9가-힣]+$",
            message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다"
    )
    private String nickname;

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다")
    @Pattern(
            regexp = "^[a-zA-Z가-힣]+$",
            message = "이름은 한글 또는 영문만 사용 가능합니다"
    )
    private String name;

    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(
            regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
            message = "전화번호 형식이 올바르지 않습니다 (예: 010-1234-5678)"
    )
    private String phone;

    @Past(message = "생년월일은 과거 날짜여야 합니다")
    private LocalDate birthDate;
}