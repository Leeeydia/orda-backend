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
     * 3. DEM 실패 시 canonical 고도는 null 처리
     *
     * @param rawLat raw GPS 위도
     * @param rawLon raw GPS 경도
     */
    public CanonicalGpsPoint process(double rawLat, double rawLon) {
        SnappedPoint snapped = trailSnapService.snap(rawLat, rawLon);

        double canonicalLat = snapped.getLatitude();
        double canonicalLon = snapped.getLongitude();

        Double demElevation = demService.getElevation(canonicalLat, canonicalLon);

        if (demElevation != null) {
            return CanonicalGpsPoint.builder()
                    .snappedLatitude(canonicalLat)
                    .snappedLongitude(canonicalLon)
                    .canonicalElevationM(demElevation)
                    .elevationSource(ElevationSource.dem)
                    .build();
        }

        log.debug("DEM 샘플링 실패 - canonical 고도 없음. lat={}, lon={}", canonicalLat, canonicalLon);
        return CanonicalGpsPoint.builder()
                .snappedLatitude(canonicalLat)
                .snappedLongitude(canonicalLon)
                .canonicalElevationM(null)
                .elevationSource(ElevationSource.none)
                .build();
    }
}