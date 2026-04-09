package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrackStatsCalculator {

    private static final double MAX_EDGE_MISSING_DISTANCE_M = 15.0;

    public double calculateTotalDistance(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }
        return points.get(points.size() - 1).getDistanceFromStartM();
    }

    public Double calculateElevationGain(List<EnrichedTrackPoint> points) {
        return calculateElevationChange(points, true);
    }

    public Double calculateElevationLoss(List<EnrichedTrackPoint> points) {
        return calculateElevationChange(points, false);
    }

    private Double calculateElevationChange(List<EnrichedTrackPoint> points, boolean gain) {
        if (!isElevationSummaryAvailable(points)) {
            return null;
        }

        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Double prev = points.get(i - 1).getElevationM();
            Double curr = points.get(i).getElevationM();

            if (prev == null || curr == null) {
                continue;
            }

            double delta = curr - prev;

            if (gain && delta > 0) {
                total += delta;
            }

            if (!gain && delta < 0) {
                total += -delta;
            }
        }
        return total;
    }

    public Double calculateMinElevation(List<EnrichedTrackPoint> points) {
        if (!isElevationSummaryAvailable(points)) {
            return null;
        }

        Double min = null;
        for (EnrichedTrackPoint point : points) {
            Double elevation = point.getElevationM();
            if (elevation == null) {
                continue;
            }
            min = (min == null) ? elevation : Math.min(min, elevation);
        }
        return min;
    }

    public Double calculateMaxElevation(List<EnrichedTrackPoint> points) {
        if (!isElevationSummaryAvailable(points)) {
            return null;
        }

        Double max = null;
        for (EnrichedTrackPoint point : points) {
            Double elevation = point.getElevationM();
            if (elevation == null) {
                continue;
            }
            max = (max == null) ? elevation : Math.max(max, elevation);
        }
        return max;
    }

    public String calculateElevationSummaryStatus(List<EnrichedTrackPoint> points) {
        if (!isElevationSummaryAvailable(points)) {
            return "UNAVAILABLE";
        }

        if (hasInterpolated(points) || hasAnyEdgeMissing(points)) {
            return "ESTIMATED";
        }

        return "COMPLETE";
    }

    public int calculatePointCount(List<EnrichedTrackPoint> points) {
        return points == null ? 0 : points.size();
    }

    public int calculateTotalDurationSeconds(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }

        Long maxElapsed = null;
        for (EnrichedTrackPoint point : points) {
            if (point.getActualElapsedSeconds() == null) {
                continue;
            }

            maxElapsed = (maxElapsed == null)
                    ? point.getActualElapsedSeconds()
                    : Math.max(maxElapsed, point.getActualElapsedSeconds());
        }

        return maxElapsed == null ? 0 : maxElapsed.intValue();
    }

    private boolean isElevationSummaryAvailable(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return false;
        }

        if (hasGap(points)) {
            return false;
        }

        if (hasInternalMissing(points)) {
            return false;
        }

        return !hasTooLargeEdgeMissing(points);
    }

    private boolean hasGap(List<EnrichedTrackPoint> points) {
        for (EnrichedTrackPoint point : points) {
            if (point.getElevationStatus() == EnrichedTrackPoint.ElevationStatus.GAP) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInternalMissing(List<EnrichedTrackPoint> points) {
        int firstNonMissingIndex = findFirstNonMissingIndex(points);
        int lastNonMissingIndex = findLastNonMissingIndex(points);

        if (firstNonMissingIndex == -1 || lastNonMissingIndex == -1) {
            return true;
        }

        for (int i = firstNonMissingIndex; i <= lastNonMissingIndex; i++) {
            if (points.get(i).getElevationStatus() == EnrichedTrackPoint.ElevationStatus.MISSING) {
                return true;
            }
        }

        return false;
    }

    private int findFirstNonMissingIndex(List<EnrichedTrackPoint> points) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).getElevationStatus() != EnrichedTrackPoint.ElevationStatus.MISSING) {
                return i;
            }
        }
        return -1;
    }

    private int findLastNonMissingIndex(List<EnrichedTrackPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).getElevationStatus() != EnrichedTrackPoint.ElevationStatus.MISSING) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasTooLargeEdgeMissing(List<EnrichedTrackPoint> points) {
        return getLeadingMissingDistance(points) > MAX_EDGE_MISSING_DISTANCE_M
                || getTrailingMissingDistance(points) > MAX_EDGE_MISSING_DISTANCE_M;
    }

    private boolean hasAnyEdgeMissing(List<EnrichedTrackPoint> points) {
        return getLeadingMissingDistance(points) > 0.0
                || getTrailingMissingDistance(points) > 0.0;
    }

    private boolean hasInterpolated(List<EnrichedTrackPoint> points) {
        for (EnrichedTrackPoint point : points) {
            if (point.getElevationStatus() == EnrichedTrackPoint.ElevationStatus.INTERPOLATED) {
                return true;
            }
        }
        return false;
    }

    private double getLeadingMissingDistance(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }

        double totalDistance = points.get(points.size() - 1).getDistanceFromStartM();

        for (EnrichedTrackPoint point : points) {
            if (point.getElevationStatus() != EnrichedTrackPoint.ElevationStatus.MISSING) {
                return point.getDistanceFromStartM();
            }
        }

        return totalDistance;
    }

    private double getTrailingMissingDistance(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }

        double totalDistance = points.get(points.size() - 1).getDistanceFromStartM();

        for (int i = points.size() - 1; i >= 0; i--) {
            EnrichedTrackPoint point = points.get(i);
            if (point.getElevationStatus() != EnrichedTrackPoint.ElevationStatus.MISSING) {
                return totalDistance - point.getDistanceFromStartM();
            }
        }

        return totalDistance;
    }
}