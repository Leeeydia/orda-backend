package com.orda.backend.domain.user.service;

import com.orda.backend.domain.user.dto.request.LoginRequest;
import com.orda.backend.domain.user.dto.request.SignupRequest;
import com.orda.backend.domain.user.dto.response.LoginResponse;
import com.orda.backend.domain.user.entity.User;
import com.orda.backend.domain.user.repository.UserRepository;
import com.orda.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    private final WebClient webClient = WebClient.create();

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
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
                .phone(request.getPhone())
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

    @Transactional
    public LoginResponse kakaoLogin(String code) {
        String kakaoAccessToken = getKakaoToken(code);
        String email = getKakaoUserEmail(kakaoAccessToken);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(email)
                                .passwordHash("")
                                .nickname(extractNickname(email))
                                .provider("kakao")
                                .build()
                ));

        String token = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail());

        return new LoginResponse(token, user.getUserId(), user.getNickname());
    }

    private String getKakaoToken(String code) {

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", kakaoRedirectUri);
        params.add("code", code);

        Map<?, ?> response = webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return (String) response.get("access_token");
    }

    private String getKakaoUserEmail(String kakaoAccessToken) {
        Map<?, ?> response = webClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Map<?, ?> kakaoAccount = (Map<?, ?>) response.get("kakao_account");
        return (String) kakaoAccount.get("email");
    }

    private String extractNickname(String email) {
        return email.split("@")[0];
    }
}