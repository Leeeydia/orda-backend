package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.dto.response.ElevationProfilePointResponse;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileSummaryResponse;
import com.orda.backend.domain.hiking.entity.GpsTrack;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//  내부 haversine 중복 제거 - TrackStatsCalculator 주입하여 거리 계산 위임
@Component
@RequiredArgsConstructor
public class ElevationProfileCalculator {

    private final TrackStatsCalculator trackStatsCalculator;

    public ElevationProfileResponse calculate(Long sessionId, List<GpsTrack> tracks) {
        if (tracks == null || tracks.size() < 2) {
            throw new IllegalArgumentException("고도 프로파일 생성을 위한 GPS 포인트가 부족합니다.");
        }

        List<GpsTrack> sortedTracks = tracks.stream()
                .sorted(Comparator.comparing(GpsTrack::getSequenceNum))
                .toList();

        List<ElevationProfilePointResponse> points = new ArrayList<>();

        double cumulativeDistanceMeters = 0.0;
        double totalElevationGainMeters = 0.0;
        double totalElevationLossMeters = 0.0;

        Double minElevationMeters = null;
        Double maxElevationMeters = null;

        GpsTrack previous = null;

        for (GpsTrack current : sortedTracks) {
            validateTrack(current);

            double latitude = current.getGeom().getY();
            double longitude = current.getGeom().getX();
            double elevation = current.getElevationM();

            double segmentDistanceMeters = 0.0;
            double elevationDiffMeters = 0.0;

            if (previous != null) {
                //  기존 내부 haversineMeters() 제거 후 TrackStatsCalculator.haversine()으로 대체
                segmentDistanceMeters = trackStatsCalculator.haversine(
                        previous.getGeom().getY(), previous.getGeom().getX(),
                        current.getGeom().getY(), current.getGeom().getX()
                );
                cumulativeDistanceMeters += segmentDistanceMeters;

                elevationDiffMeters = elevation - previous.getElevationM();

                if (elevationDiffMeters > 0) {
                    totalElevationGainMeters += elevationDiffMeters;
                } else if (elevationDiffMeters < 0) {
                    totalElevationLossMeters += Math.abs(elevationDiffMeters);
                }
            }

            minElevationMeters = (minElevationMeters == null)
                    ? elevation
                    : Math.min(minElevationMeters, elevation);

            maxElevationMeters = (maxElevationMeters == null)
                    ? elevation
                    : Math.max(maxElevationMeters, elevation);

            points.add(ElevationProfilePointResponse.builder()
                    .sequenceNum(current.getSequenceNum())
                    .latitude(latitude)
                    .longitude(longitude)
                    .elevationMeters(elevation)
                    .segmentDistanceMeters(segmentDistanceMeters)
                    .cumulativeDistanceMeters(cumulativeDistanceMeters)
                    .elevationDiffMeters(elevationDiffMeters)
                    .build());

            previous = current;
        }

        ElevationProfileSummaryResponse summary = ElevationProfileSummaryResponse.builder()
                .totalDistanceMeters(cumulativeDistanceMeters)
                .minElevationMeters(minElevationMeters)
                .maxElevationMeters(maxElevationMeters)
                .totalElevationGainMeters(totalElevationGainMeters)
                .totalElevationLossMeters(totalElevationLossMeters)
                .pointCount(points.size())
                .build();

        return ElevationProfileResponse.builder()
                .sessionId(sessionId)
                .summary(summary)
                .points(points)
                .build();
    }

    private void validateTrack(GpsTrack track) {
        if (track.getGeom() == null) {
            throw new IllegalArgumentException("GPS 좌표 정보가 없습니다. trackId=" + track.getTrackId());
        }
        if (track.getElevationM() == null) {
            throw new IllegalArgumentException("고도 정보가 없습니다. trackId=" + track.getTrackId());
        }
        if (track.getSequenceNum() == null) {
            throw new IllegalArgumentException("sequence 정보가 없습니다. trackId=" + track.getTrackId());
        }
    }
}