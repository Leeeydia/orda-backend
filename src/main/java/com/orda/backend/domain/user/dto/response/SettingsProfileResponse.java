package com.orda.backend.domain.user.dto.response;

import com.orda.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

// [윤종민] 개인 정보 수정 페이지용 프로필 응답 — 추후 name, phone, birthDate 추가 예정
@Getter
@Builder
public class SettingsProfileResponse {

    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;

    public static SettingsProfileResponse from(User user) {
        return SettingsProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}