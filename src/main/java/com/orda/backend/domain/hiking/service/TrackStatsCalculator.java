package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.entity.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.List;

// HikingService와 ElevationProfileCalculator에서 중복 사용되던 거리/고도 계산 로직을 공용 계산기로 분리
@Component
public class TrackStatsCalculator {

    // 총 이동 거리 계산 (GPS 포인트 간 거리 합산)
    public double calculateTotalDistance(List<GpsTrack> tracks) {
        double totalDistance = 0.0;
        for (int i = 1; i < tracks.size(); i++) {
            totalDistance += haversine(
                    tracks.get(i - 1).getGeom().getY(), tracks.get(i - 1).getGeom().getX(),
                    tracks.get(i).getGeom().getY(), tracks.get(i).getGeom().getX()
            );
        }
        return totalDistance;
    }

    // 총 상승 고도 계산
    public double calculateElevationGain(List<GpsTrack> tracks) {
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

    // 총 하강 고도 계산
    public double calculateElevationLoss(List<GpsTrack> tracks) {
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

    // 두 좌표 간 거리 계산 (Haversine 공식, 단위: 미터)
    public double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}