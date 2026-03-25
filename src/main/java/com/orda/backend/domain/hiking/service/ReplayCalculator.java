package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.dto.response.ReplayPointResponse;
import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ReplayCalculator {

    private static final int DEFAULT_MAX_POINTS = 300;
    private static final int DEFAULT_TARGET_DURATION_SECONDS = 60;

    public List<ReplayPointResponse> calculate(
            List<EnrichedTrackPoint> enrichedPoints,
            Integer maxPoints,
            Integer targetDurationSeconds
    ) {
        if (enrichedPoints == null || enrichedPoints.isEmpty()) {
            return Collections.emptyList();
        }

        int normalizedMaxPoints = normalizeMaxPoints(maxPoints);
        int normalizedTargetDurationSeconds = normalizeTargetDurationSeconds(targetDurationSeconds);

        List<EnrichedTrackPoint> reducedPoints = reduceByDistanceFromStart(enrichedPoints, normalizedMaxPoints);

        return toReplayPoints(reducedPoints, normalizedTargetDurationSeconds);
    }

    private int normalizeMaxPoints(Integer maxPoints) {
        if (maxPoints == null || maxPoints < 2) {
            return DEFAULT_MAX_POINTS;
        }
        return maxPoints;
    }

    private int normalizeTargetDurationSeconds(Integer targetDurationSeconds) {
        if (targetDurationSeconds == null || targetDurationSeconds < 1) {
            return DEFAULT_TARGET_DURATION_SECONDS;
        }
        return targetDurationSeconds;
    }

    private List<EnrichedTrackPoint> reduceByDistanceFromStart(
            List<EnrichedTrackPoint> points,
            int maxPoints
    ) {
        if (points.size() <= maxPoints) {
            return points;
        }

        double totalDistance = points.get(points.size() - 1).getDistanceFromStartM();

        if (totalDistance <= 0) {
            return reduceByIndex(points, maxPoints);
        }

        List<EnrichedTrackPoint> reduced = new ArrayList<>();
        reduced.add(points.get(0));

        int currentIndex = 1;
        double step = totalDistance / (maxPoints - 1);

        for (int i = 1; i < maxPoints - 1; i++) {
            double targetDistance = step * i;

            while (currentIndex < points.size() - 1
                    && points.get(currentIndex).getDistanceFromStartM() < targetDistance) {
                currentIndex++;
            }

            EnrichedTrackPoint current = points.get(currentIndex);
            EnrichedTrackPoint previous = points.get(currentIndex - 1);

            double currentDiff = Math.abs(current.getDistanceFromStartM() - targetDistance);
            double previousDiff = Math.abs(previous.getDistanceFromStartM() - targetDistance);

            EnrichedTrackPoint selected = previousDiff <= currentDiff ? previous : current;

            if (selected != reduced.get(reduced.size() - 1)) {
                reduced.add(selected);
            }
        }

        EnrichedTrackPoint lastPoint = points.get(points.size() - 1);
        if (reduced.get(reduced.size() - 1) != lastPoint) {
            reduced.add(lastPoint);
        }

        return reduced;
    }

    private List<EnrichedTrackPoint> reduceByIndex(List<EnrichedTrackPoint> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }

        List<EnrichedTrackPoint> reduced = new ArrayList<>();
        double step = (double) (points.size() - 1) / (maxPoints - 1);

        for (int i = 0; i < maxPoints; i++) {
            int index = (int) Math.round(i * step);
            if (index >= points.size()) {
                index = points.size() - 1;
            }

            EnrichedTrackPoint point = points.get(index);

            if (reduced.isEmpty() || reduced.get(reduced.size() - 1) != point) {
                reduced.add(point);
            }
        }

        EnrichedTrackPoint lastPoint = points.get(points.size() - 1);
        if (reduced.get(reduced.size() - 1) != lastPoint) {
            reduced.add(lastPoint);
        }

        return reduced;
    }

    private List<ReplayPointResponse> toReplayPoints(
            List<EnrichedTrackPoint> reducedPoints,
            int targetDurationSeconds
    ) {
        List<ReplayPointResponse> replayPoints = new ArrayList<>();
        Long totalActualElapsedSeconds = reducedPoints.get(reducedPoints.size() - 1).getActualElapsedSeconds();

        for (int i = 0; i < reducedPoints.size(); i++) {
            EnrichedTrackPoint point = reducedPoints.get(i);

            long replayElapsedSeconds = calculateReplayElapsedSeconds(
                    point.getActualElapsedSeconds(),
                    totalActualElapsedSeconds,
                    targetDurationSeconds,
                    i,
                    reducedPoints.size()
            );

            replayPoints.add(ReplayPointResponse.builder()
                    .latitude(point.getLatitude())
                    .longitude(point.getLongitude())
                    .elevationM(point.getElevationM())
                    .distanceFromStartM(point.getDistanceFromStartM())
                    .actualElapsedSeconds(point.getActualElapsedSeconds())
                    .replayElapsedSeconds(replayElapsedSeconds)
                    .build());
        }

        return replayPoints;
    }

    private long calculateReplayElapsedSeconds(
            Long actualElapsedSeconds,
            Long totalActualElapsedSeconds,
            int targetDurationSeconds,
            int currentIndex,
            int totalSize
    ) {
        if (totalSize <= 1) {
            return 0L;
        }

        if (actualElapsedSeconds == null || totalActualElapsedSeconds == null || totalActualElapsedSeconds <= 0) {
            double ratio = (double) currentIndex / (totalSize - 1);
            return Math.round(ratio * targetDurationSeconds);
        }

        double ratio = (double) actualElapsedSeconds / totalActualElapsedSeconds;
        return Math.round(ratio * targetDurationSeconds);
    }
}