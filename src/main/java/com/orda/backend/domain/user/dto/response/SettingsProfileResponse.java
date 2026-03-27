package com.orda.backend.domain.user.dto.response;

import com.orda.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 개인 정보 수정 페이지용 프로필 응답
@Getter
@Builder
public class SettingsProfileResponse {

    private Long userId;
    private String email;
    private String nickname;
    private String name;
    private String phone;
    private LocalDate birthDate;
    private String profileImageUrl;

    public static SettingsProfileResponse from(User user) {
        return SettingsProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .name(user.getName())
                .phone(user.getPhone())
                .birthDate(user.getBirthDate())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}