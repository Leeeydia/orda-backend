package com.orda.backend.domain.summit.service;

import com.orda.backend.common.exception.BusinessException;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
import com.orda.backend.domain.stats.entity.UserStats;
import com.orda.backend.domain.stats.repository.UserStatsRepository;
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
    private final HikingRecordRepository hikingRecordRepository;
    // 정상 인증 시 user_stats 업데이트를 위해 추가
    private final UserStatsRepository userStatsRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${summit.photo-upload-dir}")
    private String photoUploadDir;

    // ── 기존 GPS 인증 ──
    @Transactional
    public SummitVerifyResponse verifySummit(Long userId, SummitVerifyRequest request) {
        HikingRecord session = hikingRecordRepository.findById(request.getSessionId())
                .orElseThrow(() -> new BusinessException("존재하지 않는 등산 세션입니다. id=" + request.getSessionId()));
        session.assertOwnedBy(userId);

        NearestSummitResult nearest = summitPointRepository
                .findNearestSummit(request.getLatitude(), request.getLongitude())
                .orElseThrow(() -> new BusinessException("정상 데이터가 없습니다."));

        boolean verified = nearest.getDistance_m() <= nearest.getRadius_m();

        if (verified) {
            boolean alreadyVerified = summitVerificationRepository
                    .existsBySessionIdAndSummitIdAndVerificationMethod(
                            request.getSessionId(), nearest.getSummit_id(), "gps");

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

                // 정상 인증 완료 시 user_stats totalSummits 증가
                UserStats stats = userStatsRepository.findByUserId(userId)
                        .orElseGet(() -> UserStats.createForUser(userId));
                stats.incrementSummits();
                userStatsRepository.save(stats);
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
            Long userId, Long sessionId, Double latitude, Double longitude, MultipartFile photo) {

        HikingRecord session = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 등산 세션입니다. id=" + sessionId));
        session.assertOwnedBy(userId);

        NearestSummitResult nearest = summitPointRepository
                .findNearestSummit(latitude, longitude)
                .orElseThrow(() -> new BusinessException("정상 데이터가 없습니다."));

        boolean gpsInRange = nearest.getDistance_m() <= nearest.getRadius_m();

        log.info("사진 인증 요청 - sessionId={}, userId={}, 위치=({}, {}), 가까운 정상={}, 거리={}m, 반경={}m, GPS범위내={}",
                sessionId, userId, latitude, longitude, nearest.getName(),
                Math.round(nearest.getDistance_m()), nearest.getRadius_m(), gpsInRange);

        Map<String, Object> aiResult = openAiVisionService.analyzeSummitPhoto(photo);
        boolean aiRecognized = Boolean.TRUE.equals(aiResult.get("recognized"));
        String aiSummitName = (String) aiResult.getOrDefault("summitName", "");
        String aiElevation = (String) aiResult.getOrDefault("elevation", "");
        String aiReason = (String) aiResult.getOrDefault("reason", "");

        log.info("AI 분석 결과 - 인식={}, 산이름='{}', 고도='{}', 사유='{}'",
                aiRecognized, aiSummitName, aiElevation, aiReason);

        boolean nameMatched = !aiSummitName.isEmpty()
                && nearest.getName() != null
                && (nearest.getName().contains(aiSummitName)
                || aiSummitName.contains(nearest.getName()));

        boolean verified = gpsInRange && aiRecognized && nameMatched;

        log.info("인증 판단 - gpsInRange={}, aiRecognized={}, nameMatched={}, 최종={}",
                gpsInRange, aiRecognized, nameMatched, verified);

        if (!verified) {
            if (!gpsInRange) {
                aiReason = nearest.getName() + " 정상까지 약 " + Math.round(nearest.getDistance_m()) + "m 떨어져 있습니다";
            } else if (!aiRecognized) {
                aiReason = "정상석을 인식하지 못했습니다. 정상석이 잘 보이도록 다시 촬영해주세요";
            } else if (!nameMatched) {
                aiReason = "AI가 인식한 산(" + aiSummitName + ")과 현재 위치의 산(" + nearest.getName() + ")이 일치하지 않습니다";
            }
        }

        String photoPath = null;
        if (verified) {
            boolean alreadyVerified = summitVerificationRepository
                    .existsBySessionIdAndSummitIdAndVerificationMethod(
                            sessionId, nearest.getSummit_id(), "photo");

            if (!alreadyVerified) {
                if (photo != null && !photo.isEmpty()) {
                    photoPath = savePhoto(photo, sessionId);
                }

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

                // 사진 인증 완료 시 user_stats totalSummits 증가
                UserStats stats = userStatsRepository.findByUserId(userId)
                        .orElseGet(() -> UserStats.createForUser(userId));
                stats.incrementSummits();
                userStatsRepository.save(stats);
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

            return photoUploadDir + "/" + filename;
        } catch (IOException e) {
            log.error("사진 저장 실패", e);
            return null;
        }
    }

    public List<SessionVerifiedSummit> getVerifiedSummitsBySessionId(Long sessionId) {
        return summitVerificationRepository.findAllBySessionIdOrderByVerifiedAtAsc(sessionId)
                .stream()
                .map(this::toSessionVerifiedSummit)
                .toList();
    }

    private SessionVerifiedSummit toSessionVerifiedSummit(SummitVerification verification) {
        SummitPoint summitPoint = summitPointRepository.findById(verification.getSummitId())
                .orElseThrow(() -> new BusinessException(
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