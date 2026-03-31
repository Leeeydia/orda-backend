package com.orda.backend.domain.hiking.service;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.geojson.GeoJsonFeatureResponse;
import com.orda.backend.common.geojson.GeoJsonGeometryResponse;
import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingSessionResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
import com.orda.backend.domain.hiking.dto.response.ReplayPointResponse;
import com.orda.backend.domain.hiking.dto.response.ReplayResponse;
import com.orda.backend.domain.hiking.dto.response.ReplaySummaryResponse;
import com.orda.backend.domain.hiking.dto.response.VerifiedSummitItem;
import com.orda.backend.domain.hiking.entity.GpsTrack;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.model.CanonicalGpsPoint;
import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import com.orda.backend.domain.hiking.repository.GpsTrackRepository;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
import com.orda.backend.domain.stats.entity.UserStats;
import com.orda.backend.domain.stats.repository.UserStatsRepository;
import com.orda.backend.domain.summit.model.SessionVerifiedSummit;
import com.orda.backend.domain.summit.service.SummitService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional
    public HikingStartResponse startHiking(HikingStartRequest request) {
        HikingRecord session = HikingRecord.builder()
                .userId(request.getUserId())
                .startedAt(LocalDateTime.now())
                .build();

        HikingRecord saved = hikingRecordRepository.save(session);
        return new HikingStartResponse(saved.getId(), saved.getStartedAt());
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

            double totalDistanceM = trackStatsCalculator.calculateTotalDistance(enrichedPoints);
            double totalElevationGainM = trackStatsCalculator.calculateElevationGain(enrichedPoints);
            double totalElevationLossM = trackStatsCalculator.calculateElevationLoss(enrichedPoints);
            int totalDurationSec = trackStatsCalculator.calculateTotalDurationSeconds(enrichedPoints);

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
    public void saveGpsTrack(Long sessionId, GpsTrackRequest request) {
        if (!hikingRecordRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }

        int nextSeq = gpsTrackRepository.findMaxSequenceNum(sessionId) + 1;

        org.locationtech.jts.geom.Point geom = geometryFactory.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude())
        );

        CanonicalGpsPoint canonical = gpsTrackProcessor.process(
                request.getLatitude(),
                request.getLongitude(),
                request.getElevationM()
        );

        GpsTrack track = GpsTrack.builder()
                .sessionId(sessionId)
                .sequenceNum(nextSeq)
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

        gpsTrackRepository.save(track);
    }

    public ElevationProfileResponse getElevationProfile(Long sessionId) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        List<EnrichedTrackPoint> enrichedPoints = enrichedTrackPointBuilder.build(tracks);

        return elevationProfileBuilder.build(record.getId(), enrichedPoints);
    }

    public ReplayResponse getReplay(Long sessionId, Integer maxPoints, Integer targetDurationSeconds) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        validateReplayTracks(tracks, sessionId);

        List<EnrichedTrackPoint> enrichedPoints = enrichedTrackPointBuilder.build(tracks);

        ReplaySummaryResponse summary = ReplaySummaryResponse.builder()
                .totalDistanceMeters(trackStatsCalculator.calculateTotalDistance(enrichedPoints))
                .totalElevationGainMeters(trackStatsCalculator.calculateElevationGain(enrichedPoints))
                .totalElevationLossMeters(trackStatsCalculator.calculateElevationLoss(enrichedPoints))
                .totalElapsedSeconds(trackStatsCalculator.calculateTotalDurationSeconds(enrichedPoints))
                .build();

        List<ReplayPointResponse> replayPoints = replayCalculator.calculate(
                enrichedPoints,
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
                    properties.put("canonicalElevationM", track.getCanonicalElevationM());
                    properties.put("elevationSource", track.getElevationSource());
                    properties.put("accuracyM", track.getAccuracyM());
                    properties.put("recordedAt", track.getRecordedAt().toString());

                    return GeoJsonFeatureResponse.of(geometry, properties);
                })
                .toList();

        return GeoJsonFeatureCollectionResponse.of(features);
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