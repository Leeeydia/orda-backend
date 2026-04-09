package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EnrichedTrackPointBuilder {

    public List<EnrichedTrackPoint> build(List<GpsTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }

        List<GpsTrack> sortedTracks = tracks.stream()
                .sorted(Comparator.comparing(GpsTrack::getSequenceNum))
                .toList();

        List<EnrichedTrackPoint> enrichedPoints = new ArrayList<>();

        GpsTrack previous = null;
        double cumulativeDistanceMeters = 0.0;

        LocalDateTime startRecordedAt = sortedTracks.get(0).getRecordedAt();

        for (GpsTrack current : sortedTracks) {
            validateTrack(current);

            double latitude = current.getGeom().getY();
            double longitude = current.getGeom().getX();

            double distanceFromPrevM = 0.0;
            if (previous != null) {
                distanceFromPrevM = haversine(
                        previous.getGeom().getY(), previous.getGeom().getX(),
                        current.getGeom().getY(), current.getGeom().getX()
                );
            }

            cumulativeDistanceMeters += distanceFromPrevM;

            Long actualElapsedSeconds = null;
            if (startRecordedAt != null && current.getRecordedAt() != null) {
                actualElapsedSeconds = ChronoUnit.SECONDS.between(startRecordedAt, current.getRecordedAt());
            }

            enrichedPoints.add(EnrichedTrackPoint.builder()
                    .sequenceNum(current.getSequenceNum())
                    .latitude(latitude)
                    .longitude(longitude)
                    .elevationM(current.getCanonicalElevationM())
                    .elevationStatus(
                            current.getCanonicalElevationM() != null
                                    ? EnrichedTrackPoint.ElevationStatus.DEM
                                    : EnrichedTrackPoint.ElevationStatus.MISSING
                    )
                    .recordedAt(current.getRecordedAt())
                    .distanceFromPrevM(distanceFromPrevM)
                    .distanceFromStartM(cumulativeDistanceMeters)
                    .actualElapsedSeconds(actualElapsedSeconds)
                    .build());

            previous = current;
        }

        return enrichedPoints;
    }

    private void validateTrack(GpsTrack track) {
        if (track.getGeom() == null) {
            throw new IllegalArgumentException("GPS 좌표 정보가 없습니다. trackId=" + track.getTrackId());
        }
        if (track.getSequenceNum() == null) {
            throw new IllegalArgumentException("sequence 정보가 없습니다. trackId=" + track.getTrackId());
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}