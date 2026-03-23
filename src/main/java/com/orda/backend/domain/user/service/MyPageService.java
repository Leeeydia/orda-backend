package com.orda.backend.domain.user.service;

import com.orda.backend.domain.stats.repository.UserStatsRepository;
import com.orda.backend.domain.user.dto.request.UpdateProfileRequest;
import com.orda.backend.domain.user.dto.response.MyPageProfileResponse;
import com.orda.backend.domain.user.dto.response.MyPageStatsResponse;
import com.orda.backend.domain.user.entity.User;
import com.orda.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional(readOnly = true)
    public MyPageProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        return MyPageProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public MyPageStatsResponse getStats(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        return userStatsRepository.findByUserId(userId)
                .map(MyPageStatsResponse::from)
                .orElse(MyPageStatsResponse.empty());
    }

    @Transactional
    public MyPageProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (request.getNickname() != null) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다");
            }
            user.updateNickname(request.getNickname());
        }
        if (request.getProfileImageUrl() != null) {
            user.updateProfileImageUrl(request.getProfileImageUrl());
        }
        return MyPageProfileResponse.from(user);
    }
}