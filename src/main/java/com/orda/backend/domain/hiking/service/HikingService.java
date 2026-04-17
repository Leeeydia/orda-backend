package com.orda.backend.domain.hiking.service;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.geojson.GeoJsonFeatureResponse;
import com.orda.backend.common.geojson.GeoJsonGeometryResponse;
import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;
import com.orda.backend.domain.hiking.dto.response.GpsTrackSaveResponse;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingSessionResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
import com.orda.backend.domain.hiking.dto.response.ReplayPointResponse;
import com.orda.backend.domain.hiking.dto.response.ReplayResponse;
import com.orda.backend.domain.hiking.dto.response.ReplaySummaryResponse;
import com.orda.backend.domain.hiking.dto.response.VerifiedSummitItem;
import com.orda.backend.domain.hiking.dto.response.NearbySummitItem;
import com.orda.backend.domain.hiking.entity.GpsTrack;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.model.CanonicalGpsPoint;
import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import com.orda.backend.domain.hiking.repository.GpsTrackRepository;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
import com.orda.backend.domain.stats.entity.UserStats;
import com.orda.backend.domain.stats.repository.UserStatsRepository;
import com.orda.backend.domain.summit.model.SessionVerifiedSummit;
import com.orda.backend.domain.summit.repository.SummitPointRepository;
import com.orda.backend.domain.summit.service.SummitService;
import com.orda.backend.domain.trail.dto.response.TrailNearbyResponse;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import com.orda.backend.domain.trail.service.TrailNearbyService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HikingService {

    private final HikingRecordRepository hikingRecordRepository;
    private final GpsTrackRepository gpsTrackRepository;
    private final UserStatsRepository userStatsRepository;
    private final SummitService summitService;

    private final ElevationProfileBuilder elevationProfileBuilder;
    private final EnrichedTrackPointBuilder enrichedTrackPointBuilder;
    private final TrackStatsCalculator trackStatsCalculator;
    private final ReplayCalculator replayCalculator;
    private final GpsTrackProcessor gpsTrackProcessor;
    private final ElevationResolver elevationResolver;

    private final TrailNearbyService trailNearbyService;
    private final TrailEdgeRepository trailEdgeRepository;
    private final SummitPointRepository summitPointRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private static final double NEARBY_SUMMIT_RADIUS_M = 3000.0;

    @Value("${hiking.trail-guard.enabled:false}")
    private boolean trailGuardEnabled;

    @Transactional
    public HikingStartResponse startHiking(Long userId, HikingStartRequest request) {
        if (trailGuardEnabled) {
            validateNearTrail(request.getLatitude(), request.getLongitude());
        }

        HikingRecord session = HikingRecord.builder()
                .userId(userId)
                .startedAt(LocalDateTime.now())
                .build();

        HikingRecord saved = hikingRecordRepository.save(session);

        List<NearbySummitItem> nearbySummits = findNearbySummits(
                request.getLatitude(), request.getLongitude());

        return new HikingStartResponse(saved.getId(), saved.getStartedAt(), nearbySummits);
    }

    @Transactional
    public HikingEndResponse endHiking(Long sessionId) {
        HikingRecord session = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        LocalDateTime endedAt = LocalDateTime.now();
        session.complete(endedAt);

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        if (!tracks.isEmpty()) {
            List<EnrichedTrackPoint> enrichedPoints = enrichedTrackPointBuilder.build(tracks);
            List<EnrichedTrackPoint> resolvedPoints = elevationResolver.resolve(enrichedPoints);

            double totalDistanceM = trackStatsCalculator.calculateTotalDistance(resolvedPoints);
            Double totalElevationGainM = trackStatsCalculator.calculateElevationGain(resolvedPoints);
            Double totalElevationLossM = trackStatsCalculator.calculateElevationLoss(resolvedPoints);
            int totalDurationSec = trackStatsCalculator.calculateTotalDurationSeconds(resolvedPoints);

            session.updateStats(totalDistanceM, totalElevationGainM, totalElevationLossM, totalDurationSec);

            UserStats stats = userStatsRepository.findByUserId(session.getUserId())
                    .orElseGet(() -> {
                        UserStats newStats = UserStats.createForUser(session.getUserId());
                        return userStatsRepository.save(newStats);
                    });
            stats.addHiking(totalDistanceM, totalElevationGainM, totalDurationSec, endedAt);
        }

        return new HikingEndResponse(session.getId(), session.getEndedAt());
    }

    public HikingSessionResponse getSession(Long sessionId) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<VerifiedSummitItem> verifiedSummits = buildVerifiedSummits(record);

        return HikingSessionResponse.from(record, verifiedSummits);
    }

    @Transactional
    public GpsTrackSaveResponse saveGpsTrack(Long sessionId, GpsTrackRequest request) {
        if (!hikingRecordRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }

        // 중복 요청인지 먼저 확인 (멱등성 보장)
        Optional<GpsTrack> existing = gpsTrackRepository.findBySessionIdAndSequenceNum(
                sessionId, request.getSequenceNum());
        if (existing.isPresent()) {
            GpsTrack existingTrack = existing.get();
            return new GpsTrackSaveResponse(
                    existingTrack.getCanonicalElevationM(),
                    existingTrack.getElevationSource().name()
            );
        }

        CanonicalGpsPoint canonical = gpsTrackProcessor.process(
                request.getLatitude(),
                request.getLongitude()
        );

        org.locationtech.jts.geom.Point geom = geometryFactory.createPoint(
                new Coordinate(canonical.getSnappedLongitude(), canonical.getSnappedLatitude())
        );

        GpsTrack track = GpsTrack.builder()
                .sessionId(sessionId)
                .sequenceNum(request.getSequenceNum())
                .rawLatitude(request.getLatitude())
                .rawLongitude(request.getLongitude())
                .rawElevationM(request.getElevationM())
                .snappedLatitude(canonical.getSnappedLatitude())
                .snappedLongitude(canonical.getSnappedLongitude())
                .canonicalElevationM(canonical.getCanonicalElevationM())
                .elevationSource(canonical.getElevationSource())
                .accuracyM(request.getAccuracyM())
                .recordedAt(LocalDateTime.now())
                .geom(geom)
                .build();

        try {
            gpsTrackRepository.save(track);
        } catch (DataIntegrityViolationException e) {
            // SELECT ~ INSERT 사이 race condition으로 중복 발생한 경우
            // 영속성 컨텍스트가 오염되므로 DB 재조회 없이 이미 계산한 canonical 값으로 응답
            return new GpsTrackSaveResponse(
                    canonical.getCanonicalElevationM(),
                    canonical.getElevationSource().name()
            );
        }

        return new GpsTrackSaveResponse(
                canonical.getCanonicalElevationM(),
                canonical.getElevationSource().name()
        );
    }

    public ElevationProfileResponse getElevationProfile(Long sessionId) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        List<EnrichedTrackPoint> enrichedPoints = enrichedTrackPointBuilder.build(tracks);
        List<EnrichedTrackPoint> resolvedPoints = elevationResolver.resolve(enrichedPoints);

        return elevationProfileBuilder.build(record.getId(), resolvedPoints);
    }

    public ReplayResponse getReplay(Long sessionId, Integer maxPoints, Integer targetDurationSeconds) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        validateReplayTracks(tracks, sessionId);

        List<EnrichedTrackPoint> enrichedPoints = enrichedTrackPointBuilder.build(tracks);
        List<EnrichedTrackPoint> resolvedPoints = elevationResolver.resolve(enrichedPoints);

        ReplaySummaryResponse summary = ReplaySummaryResponse.builder()
                .totalDistanceMeters(trackStatsCalculator.calculateTotalDistance(resolvedPoints))
                .totalElevationGainMeters(trackStatsCalculator.calculateElevationGain(resolvedPoints))
                .totalElevationLossMeters(trackStatsCalculator.calculateElevationLoss(resolvedPoints))
                .totalElapsedSeconds(trackStatsCalculator.calculateTotalDurationSeconds(resolvedPoints))
                .elevationSummaryStatus(trackStatsCalculator.calculateElevationSummaryStatus(resolvedPoints))
                .build();

        List<ReplayPointResponse> replayPoints = replayCalculator.calculate(
                resolvedPoints,
                maxPoints,
                targetDurationSeconds
        );

        return ReplayResponse.builder()
                .sessionId(record.getId())
                .summary(summary)
                .points(replayPoints)
                .build();
    }

    public GeoJsonFeatureCollectionResponse getTracks(Long sessionId) {
        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);

        List<GeoJsonFeatureResponse> features = tracks.stream()
                .map(track -> {
                    GeoJsonGeometryResponse geometry = GeoJsonGeometryResponse.point(
                            track.getGeom().getX(),
                            track.getGeom().getY()
                    );
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("trackId", track.getTrackId());
                    properties.put("sequenceNum", track.getSequenceNum());

                    return GeoJsonFeatureResponse.of(geometry, properties);
                })
                .toList();

        return GeoJsonFeatureCollectionResponse.of(features);
    }

    private List<NearbySummitItem> findNearbySummits(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Collections.emptyList();
        }

        List<String> summitIds = trailEdgeRepository.findDistinctSummitIdsWithinRadius(
                longitude, latitude, NEARBY_SUMMIT_RADIUS_M);

        if (summitIds.isEmpty()) {
            return Collections.emptyList();
        }

        return summitPointRepository.findByIdIn(summitIds).stream()
                .map(sp -> NearbySummitItem.builder()
                        .summitId(sp.getId())
                        .summitName(sp.getName())
                        .latitude(sp.getGeom().getY())
                        .longitude(sp.getGeom().getX())
                        .elevationM(sp.getElevationM())
                        .build())
                .toList();
    }

    private void validateNearTrail(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("등산 시작 위치 정보가 필요합니다.");
        }

        TrailNearbyResponse result = trailNearbyService.checkNearby(latitude, longitude);

        if (!result.isNearTrail()) {
            throw new IllegalArgumentException(
                    "등산로 근처에서 시작해주세요. (반경 100m 이내)");
        }
    }

    private List<VerifiedSummitItem> buildVerifiedSummits(HikingRecord record) {
        List<SessionVerifiedSummit> verifiedSummits = summitService.getVerifiedSummitsBySessionId(record.getId());

        if (verifiedSummits.isEmpty()) {
            return Collections.emptyList();
        }

        return verifiedSummits.stream()
                .map(summit -> toVerifiedSummitItem(summit, record.getStartedAt()))
                .toList();
    }

    private VerifiedSummitItem toVerifiedSummitItem(SessionVerifiedSummit summit, LocalDateTime sessionStartedAt) {
        long verifiedElapsedSec = 0L;

        if (sessionStartedAt != null && summit.getVerifiedAt() != null) {
            verifiedElapsedSec = Math.max(
                    0L,
                    Duration.between(sessionStartedAt, summit.getVerifiedAt()).toSeconds()
            );
        }

        return VerifiedSummitItem.builder()
                .summitId(summit.getSummitId())
                .summitName(summit.getSummitName())
                .latitude(summit.getLatitude())
                .longitude(summit.getLongitude())
                .verifiedAt(summit.getVerifiedAt())
                .verifiedElapsedSec(verifiedElapsedSec)
                .build();
    }

    private void validateReplayTracks(List<GpsTrack> tracks, Long sessionId) {
        if (tracks == null || tracks.size() < 2) {
            throw new IllegalArgumentException("리플레이 생성을 위한 GPS 포인트가 부족합니다. sessionId=" + sessionId);
        }
    }
}