package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.entity.GpsTrack.ElevationSource;
import com.orda.backend.domain.hiking.model.CanonicalGpsPoint;
import com.orda.backend.domain.hiking.model.SnappedPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GpsTrackProcessor {

    private final TrailSnapService trailSnapService;
    private final DemService demService;

    /**
     * raw GPS 좌표를 canonical 기준으로 변환한다.
     *
     * 변환 순서:
     * 1. raw 좌표 → 등산로 스냅
     * 2. 스냅 좌표 기준 DEM 고도 샘플링
     * 3. DEM 실패 시 raw 고도로 fallback
     *
     * @param rawLat      raw GPS 위도
     * @param rawLon      raw GPS 경도
     * @param rawElevationM raw GPS 고도 (null 가능)
     */
    public CanonicalGpsPoint process(double rawLat, double rawLon, Double rawElevationM) {

        // 1. 등산로 스냅
        SnappedPoint snapped = trailSnapService.snap(rawLat, rawLon);

        double canonicalLat = snapped.getLatitude();
        double canonicalLon = snapped.getLongitude();

        // 2. 스냅 좌표 기준 DEM 고도 샘플링
        Double demElevation = demService.getElevation(canonicalLat, canonicalLon);

        if (demElevation != null) {
            // DEM 성공
            return CanonicalGpsPoint.builder()
                    .snappedLatitude(canonicalLat)
                    .snappedLongitude(canonicalLon)
                    .canonicalElevationM(demElevation)
                    .elevationSource(ElevationSource.dem)
                    .build();
        }

        // 3. DEM 실패 → raw 고도 fallback
        if (rawElevationM != null) {
            log.debug("DEM 샘플링 실패 - raw 고도 fallback. lat={}, lon={}", canonicalLat, canonicalLon);
            return CanonicalGpsPoint.builder()
                    .snappedLatitude(canonicalLat)
                    .snappedLongitude(canonicalLon)
                    .canonicalElevationM(rawElevationM)
                    .elevationSource(ElevationSource.gps_fallback)
                    .build();
        }

        // 4. DEM 실패 + raw 고도도 없음
        log.debug("고도 데이터 없음. lat={}, lon={}", canonicalLat, canonicalLon);
        return CanonicalGpsPoint.builder()
                .snappedLatitude(canonicalLat)
                .snappedLongitude(canonicalLon)
                .canonicalElevationM(null)
                .elevationSource(ElevationSource.none)
                .build();
    }
}