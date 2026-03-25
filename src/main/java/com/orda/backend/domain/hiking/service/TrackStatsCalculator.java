package com.orda.backend.domain.hiking.service;

import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrackStatsCalculator {

    public double calculateTotalDistance(List<EnrichedTrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }
        return points.get(points.size() - 1).getDistanceFromStartM();
    }

    public double calculateElevationGain(List<EnrichedTrackPoint> points) {
        double gain = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Double prev = points.get(i - 1).getElevationM();
            Double curr = points.get(i).getElevationM();
            if (prev != null && curr != null && curr > prev) {
                gain += curr - prev;
            }
        }
        return gain;
    }

    public double calculateElevationLoss(List<EnrichedTrackPoint> points) {
        double loss = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Double prev = points.get(i - 1).getElevationM();
            Double curr = points.get(i).getElevationM();
            if (prev != null && curr != null && curr < prev) {
                loss += prev - curr;
            }
        }
        return loss;
    }

    public Double calculateMinElevation(List<EnrichedTrackPoint> points) {
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
}