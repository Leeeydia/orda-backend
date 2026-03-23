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
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HikingService {

    private final HikingRecordRepository hikingRecordRepository;
    private final GpsTrackRepository gpsTrackRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final ElevationProfileCalculator elevationProfileCalculator;

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

    public ElevationProfileResponse getElevationProfile(Long sessionId) {
        HikingRecord record = hikingRecordRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 등산 세션입니다. id=" + sessionId));

        List<GpsTrack> tracks = gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId);

        return elevationProfileCalculator.calculate(record.getId(), tracks);
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
}