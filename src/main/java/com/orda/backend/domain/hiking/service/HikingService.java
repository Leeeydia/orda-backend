package com.orda.backend.domain.hiking.service;

import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.geojson.GeoJsonFeatureResponse;
import com.orda.backend.common.geojson.GeoJsonGeometryResponse;
import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.HikingEndResponse;
import com.orda.backend.domain.hiking.dto.response.HikingSessionResponse;
import com.orda.backend.domain.hiking.dto.response.HikingStartResponse;
import com.orda.backend.domain.hiking.entity.GpsTrack;
import com.orda.backend.domain.hiking.entity.HikingRecord;
import com.orda.backend.domain.hiking.repository.GpsTrackRepository;
import com.orda.backend.domain.hiking.repository.HikingRecordRepository;
import com.orda.backend.domain.stats.entity.UserStats;
import com.orda.backend.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HikingService {

    private final HikingRecordRepository hikingRecordRepository;
    private final GpsTrackRepository gpsTrackRepository;

    // [윤종민] 등산 종료 시 user_stats 업데이트를 위해 추가
    private final UserStatsRepository userStatsRepository;

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

        // [윤종민] GPS 트랙 집계 후 세션 통계 및 user_stats 업데이트
        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);
        if (!tracks.isEmpty()) {
            double totalDistanceM = calculateTotalDistance(tracks);
            double totalElevationGainM = calculateElevationGain(tracks);
            double totalElevationLossM = calculateElevationLoss(tracks);
            int totalDurationSec = (int) ChronoUnit.SECONDS.between(session.getStartedAt(), endedAt);

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
        return HikingSessionResponse.from(record);
    }

    @Transactional
    public void saveGpsTrack(Long sessionId, GpsTrackRequest request) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId));

        int nextSeq = gpsTrackRepository.findMaxSequenceNum(sessionId) + 1;

        org.locationtech.jts.geom.Point point = geometryFactory.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude())
        );

        GpsTrack track = GpsTrack.builder()
                .hikingRecord(record)
                .sequenceNum(nextSeq)
                .elevationM(request.getElevationM())
                .accuracyM(request.getAccuracyM())
                .recordedAt(LocalDateTime.now())
                .geom(point)
                .build();

        gpsTrackRepository.save(track);
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
                    properties.put("elevationM", track.getElevationM());
                    properties.put("accuracyM", track.getAccuracyM());
                    properties.put("recordedAt", track.getRecordedAt().toString());

                    return GeoJsonFeatureResponse.of(geometry, properties);
                })
                .toList();

        return GeoJsonFeatureCollectionResponse.of(features);
    }

    // [윤종민] GPS 포인트 간 거리 합산 (Haversine 공식 사용)
    private double calculateTotalDistance(List<GpsTrack> tracks) {
        double totalDistance = 0.0;
        for (int i = 1; i < tracks.size(); i++) {
            totalDistance += haversine(
                    tracks.get(i - 1).getGeom().getY(), tracks.get(i - 1).getGeom().getX(),
                    tracks.get(i).getGeom().getY(), tracks.get(i).getGeom().getX()
            );
        }
        return totalDistance;
    }

    // [윤종민] 고도 상승분 합산
    private double calculateElevationGain(List<GpsTrack> tracks) {
        double gain = 0.0;
        for (int i = 1; i < tracks.size(); i++) {
            Double prev = tracks.get(i - 1).getElevationM();
            Double curr = tracks.get(i).getElevationM();
            if (prev != null && curr != null && curr > prev) {
                gain += curr - prev;
            }
        }
        return gain;
    }

    // [윤종민] 고도 하강분 합산
    private double calculateElevationLoss(List<GpsTrack> tracks) {
        double loss = 0.0;
        for (int i = 1; i < tracks.size(); i++) {
            Double prev = tracks.get(i - 1).getElevationM();
            Double curr = tracks.get(i).getElevationM();
            if (prev != null && curr != null && curr < prev) {
                loss += prev - curr;
            }
        }
        return loss;
    }

    // [윤종민] 두 좌표 간 거리 계산 (단위: 미터)
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}