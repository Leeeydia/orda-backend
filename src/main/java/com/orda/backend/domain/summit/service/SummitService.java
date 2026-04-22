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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SummitService {

    private final SummitPointRepository summitPointRepository;
    private final SummitVerificationRepository summitVerificationRepository;
    private final HikingRecordRepository hikingRecordRepository;
    // 정상 인증 시 user_stats 업데이트를 위해 추가
    private final UserStatsRepository userStatsRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

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
                        .geom(userPoint)
                        .build();

                summitVerificationRepository.save(verification);

                // 정상 인증 완료 시 user_stats totalSummits 증가
                UserStats stats = userStatsRepository.findByUserId(userId)
                        .orElseGet(() -> userStatsRepository.save(UserStats.createForUser(userId)));
                stats.incrementSummits();
            }
        }

        return SummitVerifyResponse.builder()
                .verified(verified)
                .summitId(nearest.getSummit_id())
                .summitName(nearest.getName())
                .distanceM(nearest.getDistance_m())
                .build();
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