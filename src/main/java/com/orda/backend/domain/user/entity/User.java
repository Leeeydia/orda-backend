package com.orda.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = true, length = 50)       // 카카오 로그인 시 null 허용
    private String name;

    @Column(nullable = true, unique = true, length = 20)  // 카카오 로그인 시 null 허용
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(nullable = false, length = 20)

    private String provider;         // local / kakao

    @Column(name = "kakao_id", unique = true, length = 255)
    private String kakaoId;                     // 카카오 고유 식별자

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public User(String email, String passwordHash, String nickname,
                String name, String phone, LocalDate birthDate,
                String provider, String kakaoId) {
        this.email = email;
        this.passwordHash = passwordHash != null ? passwordHash : "";
        this.nickname = nickname;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.provider = provider != null ? provider : "local";
        this.kakaoId = kakaoId;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updatePhone(String phone) {
        this.phone = phone;
    }

    public void updateBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    // [윤종민] 비밀번호 변경을 위해 추가
    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}