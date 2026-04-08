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
import reactor.core.publisher.Mono;

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
        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);

        User user = userRepository.findByProviderAndKakaoId("kakao", kakaoUserInfo.kakaoId())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(kakaoUserInfo.email())
                                .passwordHash("")
                                .nickname(generateUniqueNickname(kakaoUserInfo.email()))
                                .provider("kakao")
                                .kakaoId(kakaoUserInfo.kakaoId())
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

        Map<?, ?> response;
        try {
            response = webClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(params)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            res -> Mono.error(new IllegalArgumentException("유효하지 않은 카카오 인가 코드입니다")))
                    .onStatus(status -> status.is5xxServerError(),
                            res -> Mono.error(new IllegalStateException("카카오 서버 오류입니다")))
                    .bodyToMono(Map.class)
                    .block();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("카카오 토큰 요청 중 오류가 발생했습니다");
        }

        String accessToken = (String) response.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("카카오 토큰 응답에 access_token이 없습니다");
        }
        return accessToken;
    }

    private KakaoUserInfo getKakaoUserInfo(String kakaoAccessToken) {
        Map<?, ?> response;
        try {
            response = webClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            res -> Mono.error(new IllegalArgumentException("카카오 사용자 정보 조회에 실패했습니다")))
                    .onStatus(status -> status.is5xxServerError(),
                            res -> Mono.error(new IllegalStateException("카카오 서버 오류입니다")))
                    .bodyToMono(Map.class)
                    .block();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("카카오 사용자 정보 요청 중 오류가 발생했습니다");
        }

        Object idObj = response.get("id");
        if (idObj == null) {
            throw new IllegalStateException("카카오 응답에 사용자 id가 없습니다");
        }
        String kakaoId = String.valueOf(idObj);

        Map<?, ?> kakaoAccount = (Map<?, ?>) response.get("kakao_account");
        String email = (kakaoAccount != null) ? (String) kakaoAccount.get("email") : null;
        if (email == null) {
            throw new IllegalArgumentException("카카오 계정에 이메일 제공 동의가 필요합니다");
        }

        return new KakaoUserInfo(kakaoId, email);
    }

    private String generateUniqueNickname(String email) {
        String base = (email != null) ? email.split("@")[0] : "user";
        String candidate = base;
        int suffix = 1;

        while (userRepository.existsByNickname(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private record KakaoUserInfo(String kakaoId, String email) {}
}