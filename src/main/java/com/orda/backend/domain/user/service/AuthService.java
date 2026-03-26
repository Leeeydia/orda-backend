package com.orda.backend.domain.user.service;

import com.orda.backend.domain.user.dto.request.LoginRequest;
import com.orda.backend.domain.user.dto.request.SignupRequest;
import com.orda.backend.domain.user.dto.response.LoginResponse;
import com.orda.backend.domain.user.entity.User;
import com.orda.backend.domain.user.repository.UserRepository;
import com.orda.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        String normalizedPhone = request.getPhone().replaceAll("-", "");

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다");
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("이미 사용 중인 전화번호입니다");
        }
        if (request.getPassword().equalsIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("비밀번호는 이메일과 동일할 수 없습니다");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .name(request.getName())
                .phone(normalizedPhone)
                .birthDate(request.getBirthDate())
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        String token = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail());

        return new LoginResponse(token, user.getUserId(), user.getNickname());
    }
}