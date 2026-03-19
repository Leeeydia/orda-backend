package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.dto.request.GpsTrackRequest;
import com.orda.backend.domain.hiking.dto.request.HikingStartRequest;
import com.orda.backend.domain.hiking.dto.response.GpsTrackResponse;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HikingService {

    private final HikingRecordRepository hikingRecordRepository;
    private final GpsTrackRepository gpsTrackRepository;
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

    public List<GpsTrackResponse> getTracks(Long sessionId) {
        return gpsTrackRepository.findBySessionIdOrderBySequenceNum(sessionId)
                .stream()
                .map(GpsTrackResponse::new)
                .toList();
    }
}