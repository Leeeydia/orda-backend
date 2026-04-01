package com.orda.backend.domain.hiking.dto.response;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GpsTrackResponse {

    private final Long trackId;
    private final Integer sequenceNum;
    private final Double latitude;
    private final Double longitude;
    private final Double elevationM;
    private final Double accuracyM;
    private final LocalDateTime recordedAt;

    public GpsTrackResponse(GpsTrack track) {
        this.trackId = track.getTrackId();
        this.sequenceNum = track.getSequenceNum();
        this.latitude = track.getGeom().getY();
        this.longitude = track.getGeom().getX();
        this.elevationM = track.getCanonicalElevationM(); // 수정
        this.accuracyM = track.getAccuracyM();
        this.recordedAt = track.getRecordedAt();
    }
}