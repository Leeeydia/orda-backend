package com.orda.backend.domain.user.dto.response;

import com.orda.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class MyPageProfileResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;

    public static MyPageProfileResponse from(User user) {
        return MyPageProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}