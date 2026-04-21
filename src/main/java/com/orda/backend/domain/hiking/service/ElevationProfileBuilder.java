package com.orda.backend.domain.hiking.service;

import com.orda.backend.common.exception.BusinessException;
import com.orda.backend.domain.hiking.dto.response.ElevationProfilePointResponse;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileResponse;
import com.orda.backend.domain.hiking.dto.response.ElevationProfileSummaryResponse;
import com.orda.backend.domain.hiking.model.EnrichedTrackPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ElevationProfileBuilder {

    private final TrackStatsCalculator trackStatsCalculator;

    public ElevationProfileResponse build(Long sessionId, List<EnrichedTrackPoint> points) {
        if (points == null || points.size() < 2) {
            throw new BusinessException("고도 프로파일 생성을 위한 GPS 포인트가 부족합니다.");
        }

        List<ElevationProfilePointResponse> responsePoints = new ArrayList<>();

        EnrichedTrackPoint previous = null;
        for (EnrichedTrackPoint current : points) {
            validateSequence(current);

            Double elevationDiffMeters = null;
            if (previous != null && previous.getElevationM() != null && current.getElevationM() != null) {
                elevationDiffMeters = current.getElevationM() - previous.getElevationM();
            }

            responsePoints.add(ElevationProfilePointResponse.builder()
                    .sequenceNum(current.getSequenceNum())
                    .latitude(current.getLatitude())
                    .longitude(current.getLongitude())
                    .elevationMeters(current.getElevationM())
                    .elevationStatus(
                            current.getElevationStatus() != null
                                    ? current.getElevationStatus().name()
                                    : null
                    )
                    .segmentDistanceMeters(current.getDistanceFromPrevM())
                    .cumulativeDistanceMeters(current.getDistanceFromStartM())
                    .elevationDiffMeters(elevationDiffMeters)
                    .build());

            previous = current;
        }

        ElevationProfileSummaryResponse summary = ElevationProfileSummaryResponse.builder()
                .totalDistanceMeters(trackStatsCalculator.calculateTotalDistance(points))
                .minElevationMeters(trackStatsCalculator.calculateMinElevation(points))
                .maxElevationMeters(trackStatsCalculator.calculateMaxElevation(points))
                .totalElevationGainMeters(trackStatsCalculator.calculateElevationGain(points))
                .totalElevationLossMeters(trackStatsCalculator.calculateElevationLoss(points))
                .elevationSummaryStatus(trackStatsCalculator.calculateElevationSummaryStatus(points))
                .pointCount(trackStatsCalculator.calculatePointCount(points))
                .build();

        return ElevationProfileResponse.builder()
                .sessionId(sessionId)
                .summary(summary)
                .points(responsePoints)
                .build();
    }

    private void validateSequence(EnrichedTrackPoint point) {
        if (point.getSequenceNum() == null) {
            throw new BusinessException("sequence 정보가 없습니다.");
        }
    }
}