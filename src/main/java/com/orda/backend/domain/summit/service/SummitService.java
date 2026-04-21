package com.orda.backend.domain.summit.service;

import com.orda.backend.common.exception.BusinessException;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
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
            Long userId, Long sessionId, Double latitude, Double longitude, MultipartFile photo) {

        // 세션 소유자 검증
        HikingRecord session = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 등산 세션입니다. id=" + sessionId));
        session.assertOwnedBy(userId);

        // 1. GPS로 가장 가까운 정상 조회
        NearestSummitResult nearest = summitPointRepository
                .findNearestSummit(latitude, longitude)
                .orElseThrow(() -> new BusinessException("정상 데이터가 없습니다."));

        boolean gpsInRange = nearest.getDistance_m() <= nearest.getRadius_m();

        log.info("사진 인증 요청 - sessionId={}, userId={}, 위치=({}, {}), 가까운 정상={}, 거리={}m, 반경={}m, GPS범위내={}",
                sessionId, userId, latitude, longitude, nearest.getName(),
                Math.round(nearest.getDistance_m()), nearest.getRadius_m(), gpsInRange);

        // 2. AI 사진 분석
        Map<String, Object> aiResult = openAiVisionService.analyzeSummitPhoto(photo);
        boolean aiRecognized = Boolean.TRUE.equals(aiResult.get("recognized"));
        String aiSummitName = (String) aiResult.getOrDefault("summitName", "");
        String aiElevation = (String) aiResult.getOrDefault("elevation", "");
        String aiReason = (String) aiResult.getOrDefault("reason", "");

        log.info("AI 분석 결과 - 인식={}, 산이름='{}', 고도='{}', 사유='{}'",
                aiRecognized, aiSummitName, aiElevation, aiReason);

        // 3. AI 인식된 산 이름과 DB 정상 이름 매칭
        boolean nameMatched = !aiSummitName.isEmpty()
                && nearest.getName() != null
                && (nearest.getName().contains(aiSummitName)
                || aiSummitName.contains(nearest.getName()));

        // 4. 최종 인증 판단: GPS 반경 내 + AI 인식 성공 + 이름 매칭
        boolean verified = gpsInRange && aiRecognized && nameMatched;

        log.info("인증 판단 - gpsInRange={}, aiRecognized={}, nameMatched={}, 최종={}",
                gpsInRange, aiRecognized, nameMatched, verified);

        // 5. 실패 사유 구체화
        if (!verified) {
            if (!gpsInRange) {
                aiReason = nearest.getName() + " 정상까지 약 " + Math.round(nearest.getDistance_m()) + "m 떨어져 있습니다";
            } else if (!aiRecognized) {
                aiReason = "정상석을 인식하지 못했습니다. 정상석이 잘 보이도록 다시 촬영해주세요";
            } else if (!nameMatched) {
                aiReason = "AI가 인식한 산(" + aiSummitName + ")과 현재 위치의 산(" + nearest.getName() + ")이 일치하지 않습니다";
            }
        }

        // 6. 인증 성공 시에만 사진 저장 + DB 저장
        String photoPath = null;
        if (verified) {
            if (photo != null && !photo.isEmpty()) {
                photoPath = savePhoto(photo, sessionId);
            }

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

            // 상대 경로 반환
            return photoUploadDir + "/" + filename;
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