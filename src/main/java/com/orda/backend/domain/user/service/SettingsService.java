package com.orda.backend.domain.user.service;

import com.orda.backend.domain.user.dto.request.ChangePasswordRequest;
import com.orda.backend.domain.user.dto.request.UpdateProfileRequest;
import com.orda.backend.domain.user.dto.response.SettingsProfileResponse;
import com.orda.backend.domain.user.entity.User;
import com.orda.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [윤종민] 개인 정보 수정 서비스 — 마이페이지에서 분리
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public SettingsProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        return SettingsProfileResponse.from(user);
    }

    // [윤종민] 프로필 수정 — 추후 name, phone, birthDate 추가 예정
    @Transactional
    public SettingsProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        if (request.getNickname() != null) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다");
            }
            user.updateNickname(request.getNickname());
        }

        return SettingsProfileResponse.from(user);
    }

    // [윤종민] 비밀번호 변경
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다");
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }
}