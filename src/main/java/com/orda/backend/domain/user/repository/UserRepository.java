package com.orda.backend.domain.user.repository;

import com.orda.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndKakaoId(String provider, String kakaoId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByPhone(String phone);
    boolean existsByNicknameAndUserIdNot(String nickname, Long userId); // 추가: 자기 자신 제외 닉네임 중복 체크
    boolean existsByPhoneAndUserIdNot(String phone, Long userId); // 추가: 자기 자신 제외 전화번호 중복 체크
}