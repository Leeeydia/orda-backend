package com.orda.backend.domain.summit.service;

import com.orda.backend.domain.summit.dto.request.SummitVerifyRequest;
import com.orda.backend.domain.summit.dto.response.SummitVerifyResponse;
import com.orda.backend.domain.summit.entity.SummitPoint;
import com.orda.backend.domain.summit.entity.SummitVerification;
import com.orda.backend.domain.summit.model.SessionVerifiedSummit;
import com.orda.backend.domain.summit.repository.NearestSummitResult;
import com.orda.backend.domain.summit.repository.SummitPointRepository;
import com.orda.backend.domain.summit.repository.SummitVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SummitService {

    private final SummitPointRepository summitPointRepository;
    private final SummitVerificationRepository summitVerificationRepository;
    private final OpenAiVisionService openAiVisionService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${summit.photo-upload-dir}")
    private String photoUploadDir;

    // ── 기존 GPS 인증 ──
    @Transactional
    public SummitVerifyResponse verifySummit(SummitVerifyRequest request) {
        NearestSummitResult nearest = summitPointRepository
                .findNearestSummit(request.getLatitude(), request.getLongitude())
                .orElseThrow(() -> new IllegalArgumentException("정상 데이터가 없습니다."));

        boolean verified = nearest.getDistance_m() <= nearest.getRadius_m();

        if (verified) {
            boolean alreadyVerified = summitVerificationRepository
                    .existsBySessionIdAndSummitId(request.getSessionId(), nearest.getSummit_id());

            if (!alreadyVerified) {
                Point userPoint = geometryFactory.createPoint(
                        new Coordinate(request.getLongitude(), request.getLatitude())
                );

                SummitVerification verification = SummitVerification.builder()
                        .sessionId(request.getSessionId())
                        .summitId(nearest.getSummit_id())
                        .distanceToSummitM(nearest.getDistance_m())
                        .verificationMethod("gps")
                        .geom(userPoint)
                        .build();

                summitVerificationRepository.save(verification);
            }
        }

        return SummitVerifyResponse.builder()
                .verified(verified)
                .summitId(nearest.getSummit_id())
                .summitName(nearest.getName())
                .distanceM(nearest.getDistance_m())
                .verificationMethod("gps")
                .build();
    }

    // ── 사진 + AI + GPS 통합 인증 ──
    @Transactional
    public SummitVerifyResponse verifySummitWithPhoto(
            Long sessionId, Double latitude, Double longitude, MultipartFile photo) {

        // 1. GPS로 가장 가까운 정상 조회
        NearestSummitResult nearest = summitPointRepository
                .findNearestSummit(latitude, longitude)
                .orElseThrow(() -> new IllegalArgumentException("정상 데이터가 없습니다."));

        boolean gpsInRange = nearest.getDistance_m() <= nearest.getRadius_m();

        // 2. AI 사진 분석
        Map<String, Object> aiResult = openAiVisionService.analyzeSummitPhoto(photo);
        boolean aiRecognized = Boolean.TRUE.equals(aiResult.get("recognized"));
        String aiSummitName = (String) aiResult.getOrDefault("summitName", "");
        String aiElevation = (String) aiResult.getOrDefault("elevation", "");
        String aiReason = (String) aiResult.getOrDefault("reason", "");

        // 3. AI 인식된 산 이름과 DB 정상 이름 매칭
        boolean nameMatched = !aiSummitName.isEmpty()
                && nearest.getName() != null
                && (nearest.getName().contains(aiSummitName)
                || aiSummitName.contains(nearest.getName()));

        // 4. 최종 인증 판단: GPS 반경 내 + AI 인식 성공 + 이름 매칭
        boolean verified = gpsInRange && aiRecognized && nameMatched;

        // 5. 사진 저장
        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            photoPath = savePhoto(photo, sessionId);
        }

        // 6. 인증 성공 시 DB 저장
        if (verified) {
            boolean alreadyVerified = summitVerificationRepository
                    .existsBySessionIdAndSummitId(sessionId, nearest.getSummit_id());

            if (!alreadyVerified) {
                Point userPoint = geometryFactory.createPoint(
                        new Coordinate(longitude, latitude)
                );

                SummitVerification verification = SummitVerification.builder()
                        .sessionId(sessionId)
                        .summitId(nearest.getSummit_id())
                        .distanceToSummitM(nearest.getDistance_m())
                        .verificationMethod("photo")
                        .photoPath(photoPath)
                        .geom(userPoint)
                        .build();

                summitVerificationRepository.save(verification);
            }
        }

        return SummitVerifyResponse.builder()
                .verified(verified)
                .summitId(nearest.getSummit_id())
                .summitName(nearest.getName())
                .distanceM(nearest.getDistance_m())
                .verificationMethod("photo")
                .photoPath(photoPath)
                .aiRecognizedName(aiSummitName)
                .aiRecognizedElevation(aiElevation)
                .aiReason(aiReason)
                .build();
    }

    private String savePhoto(MultipartFile photo, Long sessionId) {
        try {
            Path uploadPath = Paths.get(photoUploadDir).toAbsolutePath();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "summit_" + sessionId + "_" + UUID.randomUUID() + ".jpg";
            Path filePath = uploadPath.resolve(filename);
            photo.transferTo(filePath);

            return filePath.toString();
        } catch (IOException e) {
            log.error("사진 저장 실패", e);
            return null;
        }
    }

    // ── 기존 메서드 유지 ──
    public List<SessionVerifiedSummit> getVerifiedSummitsBySessionId(Long sessionId) {
        return summitVerificationRepository.findAllBySessionIdOrderByVerifiedAtAsc(sessionId)
                .stream()
                .map(this::toSessionVerifiedSummit)
                .toList();
    }

    private SessionVerifiedSummit toSessionVerifiedSummit(SummitVerification verification) {
        SummitPoint summitPoint = summitPointRepository.findById(verification.getSummitId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "정상 정보를 찾을 수 없습니다. summitId=" + verification.getSummitId()
                ));

        Point summitGeom = summitPoint.getGeom();

        return SessionVerifiedSummit.builder()
                .summitId(summitPoint.getId())
                .summitName(summitPoint.getName())
                .latitude(summitGeom.getY())
                .longitude(summitGeom.getX())
                .verifiedAt(verification.getVerifiedAt())
                .build();
    }
}