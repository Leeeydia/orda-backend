package com.orda.backend.domain.user.service;

import com.orda.backend.common.exception.BusinessException;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
import com.orda.backend.domain.stats.repository.UserStatsRepository;
import com.orda.backend.domain.user.dto.response.MyPageHikingRecordResponse;
import com.orda.backend.domain.user.dto.response.MyPageProfileResponse;
import com.orda.backend.domain.user.dto.response.MyPageStatsResponse;
import com.orda.backend.domain.user.entity.User;
import com.orda.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final HikingRecordRepository hikingRecordRepository;

    //  프로필 이미지 로컬 저장 경로 (application.yml에서 설정)
    @Value("${file.upload-dir}")
    private String uploadDir;

    //  허용 확장자 목록
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");

    //  최대 파일 크기 5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Transactional(readOnly = true)
    public MyPageProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다"));
        return MyPageProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public MyPageStatsResponse getStats(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다"));
        return userStatsRepository.findByUserId(userId)
                .map(MyPageStatsResponse::from)
                .orElse(MyPageStatsResponse.empty());
    }

    @Transactional(readOnly = true)
    public List<MyPageHikingRecordResponse> getHikingRecords(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다"));
        List<HikingRecord> records = hikingRecordRepository.findByUserIdOrderByStartedAtDesc(userId);
        return records.stream()
                .map(MyPageHikingRecordResponse::from)
                .collect(Collectors.toList());
    }

    //  프로필 이미지 파일 업로드 - 로컬 저장 방식
    @Transactional
    public String uploadProfileImage(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다"));

        validateFile(file);
        deleteExistingImage(user.getProfileImageUrl());

        String fileName = saveFile(file);
        String imageUrl = "/uploads/profile-images/" + fileName;

        user.updateProfileImageUrl(imageUrl);
        return imageUrl;
    }

    //  프로필 이미지 삭제 - 파일 삭제 + DB null 처리
    @Transactional
    public void deleteProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다"));

        if (user.getProfileImageUrl() == null) {
            throw new BusinessException("삭제할 프로필 이미지가 없습니다");
        }

        deleteExistingImage(user.getProfileImageUrl());
        user.updateProfileImageUrl(null);
    }

    //  파일 크기 및 확장자 유효성 검사
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("파일이 없습니다");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("파일 크기는 5MB 이하여야 합니다");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException("jpg, jpeg, png 파일만 업로드 가능합니다");
        }
    }

    //  UUID 기반 파일명으로 저장 (중복 방지)
    private String saveFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(uploadPath);

            String extension = getExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + "." + extension;
            Path filePath = uploadPath.resolve(fileName);

            file.transferTo(filePath.toFile());
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장에 실패했습니다");
        }
    }

    //  기존 파일 삭제 (파일이 없어도 예외 없이 통과)
    private void deleteExistingImage(String imageUrl) {
        if (imageUrl == null) return;
        try {
            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir).toAbsolutePath().resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // 파일 삭제 실패해도 업로드는 계속 진행
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException("올바르지 않은 파일명입니다");
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}