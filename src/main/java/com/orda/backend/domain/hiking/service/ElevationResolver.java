package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ElevationResolver {

    private static final double MAX_INTERPOLATION_GAP_DISTANCE_M = 50.0;
    private static final int MAX_CONSECUTIVE_MISSING_POINTS = 3;

    public List<EnrichedTrackPoint> resolve(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        List<EnrichedTrackPoint> resolved = new ArrayList<>(points);

        int index = 0;
        while (index < resolved.size()) {
            if (resolved.get(index).getElevationStatus() != EnrichedTrackPoint.ElevationStatus.MISSING) {
                index++;
                continue;
            }

            int start = index;
            while (index < resolved.size()
                    && resolved.get(index).getElevationStatus() == EnrichedTrackPoint.ElevationStatus.MISSING) {
                index++;
            }
            int end = index - 1;

            if (canInterpolate(resolved, start, end)) {
                interpolateRange(resolved, start, end);
            } else {
                markUnresolved(resolved, start, end);
            }
        }

        return resolved;
    }

    private boolean canInterpolate(List<EnrichedTrackPoint> points, int start, int end) {
        // 시작/끝 결손은 보간 불가
        if (start <= 0 || end >= points.size() - 1) {
            return false;
        }

        int missingCount = end - start + 1;
        if (missingCount > MAX_CONSECUTIVE_MISSING_POINTS) {
            return false;
        }

        EnrichedTrackPoint previous = points.get(start - 1);
        EnrichedTrackPoint next = points.get(end + 1);

        if (!isUsableAnchor(previous) || !isUsableAnchor(next)) {
            return false;
        }

        double gapDistance = next.getDistanceFromStartM() - previous.getDistanceFromStartM();
        return gapDistance > 0 && gapDistance <= MAX_INTERPOLATION_GAP_DISTANCE_M;
    }

    private boolean isUsableAnchor(EnrichedTrackPoint point) {
        if (point.getElevationM() == null) {
            return false;
        }

        return point.getElevationStatus() == EnrichedTrackPoint.ElevationStatus.DEM
                || point.getElevationStatus() == EnrichedTrackPoint.ElevationStatus.INTERPOLATED;
    }

    private void interpolateRange(List<EnrichedTrackPoint> points, int start, int end) {
        EnrichedTrackPoint previous = points.get(start - 1);
        EnrichedTrackPoint next = points.get(end + 1);

        double startDistance = previous.getDistanceFromStartM();
        double totalDistance = next.getDistanceFromStartM() - startDistance;
        double elevationDelta = next.getElevationM() - previous.getElevationM();

        for (int i = start; i <= end; i++) {
            EnrichedTrackPoint current = points.get(i);

            double ratio;
            if (totalDistance <= 0) {
                ratio = (double) (i - start + 1) / (end - start + 2);
            } else {
                ratio = (current.getDistanceFromStartM() - startDistance) / totalDistance;
            }

            double interpolatedElevation = previous.getElevationM() + (elevationDelta * ratio);

            points.set(i, current.toBuilder()
                    .elevationM(interpolatedElevation)
                    .elevationStatus(EnrichedTrackPoint.ElevationStatus.INTERPOLATED)
                    .build());
        }
    }

    private void markUnresolved(List<EnrichedTrackPoint> points, int start, int end) {
        EnrichedTrackPoint.ElevationStatus status =
                (start > 0 && end < points.size() - 1)
                        ? EnrichedTrackPoint.ElevationStatus.GAP
                        : EnrichedTrackPoint.ElevationStatus.MISSING;

        for (int i = start; i <= end; i++) {
            EnrichedTrackPoint current = points.get(i);
            points.set(i, current.toBuilder()
                    .elevationStatus(status)
                    .build());
        }
    }
}