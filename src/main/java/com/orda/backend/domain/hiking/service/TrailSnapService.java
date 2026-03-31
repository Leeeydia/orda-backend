package com.orda.backend.domain.hiking.service;

import com.orda.backend.common.projection.SnappedPointProjection; // 변경
import com.orda.backend.domain.hiking.model.SnappedPoint;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository; // 변경
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrailSnapService {

    private static final double SNAP_RADIUS_M = 50.0;

    private final TrailEdgeRepository trailEdgeRepository;

    public SnappedPoint snap(double rawLat, double rawLon) {
        Optional<SnappedPointProjection> result =
                trailEdgeRepository.findClosestPoint(rawLon, rawLat, SNAP_RADIUS_M);

        if (result.isEmpty()) {
            log.debug("스냅 실패 - 반경 {}m 내 등산로 없음. lat={}, lon={}", SNAP_RADIUS_M, rawLat, rawLon);
            return SnappedPoint.builder()
                    .latitude(rawLat)
                    .longitude(rawLon)
                    .distanceM(0.0)
                    .snapped(false)
                    .build();
        }

        SnappedPointProjection projection = result.get();
        return SnappedPoint.builder()
                .latitude(projection.getSnappedLat())
                .longitude(projection.getSnappedLon())
                .distanceM(projection.getDistanceM())
                .snapped(true)
                .build();
    }
}