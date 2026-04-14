package com.orda.backend.domain.trail.service;

import com.orda.backend.common.projection.SnappedPointProjection;
import com.orda.backend.domain.trail.dto.response.TrailNearbyResponse;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrailNearbyService {

    private static final double CHECK_RADIUS_M = 100.0;

    private final TrailEdgeRepository trailEdgeRepository;

    public TrailNearbyResponse checkNearby(double lat, double lng) {
        Optional<SnappedPointProjection> result =
                trailEdgeRepository.findClosestPoint(lng, lat, CHECK_RADIUS_M);

        if (result.isEmpty()) {
            return new TrailNearbyResponse(false, null);
        }

        return new TrailNearbyResponse(true, result.get().getDistanceM());
    }
}