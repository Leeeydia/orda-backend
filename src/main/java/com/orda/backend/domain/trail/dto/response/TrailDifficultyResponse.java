package com.orda.backend.domain.trail.dto.response;

import com.orda.backend.domain.trail.entity.TrailEdge;
import lombok.Getter;

@Getter
public class TrailDifficultyResponse {

    private final String edgeId;
    private final Double distanceM;
    private final Double slopePercent;
    private final Double elevationDiffM;
    private final Double difficultyScore;
    private final String difficulty;
    private final String nearestSummitId;

    private TrailDifficultyResponse(
            String edgeId,
            Double distanceM,
            Double slopePercent,
            Double elevationDiffM,
            Double difficultyScore,
            String difficulty,
            String nearestSummitId
    ) {
        this.edgeId = edgeId;
        this.distanceM = distanceM;
        this.slopePercent = slopePercent;
        this.elevationDiffM = elevationDiffM;
        this.difficultyScore = difficultyScore;
        this.difficulty = difficulty;
        this.nearestSummitId = nearestSummitId;
    }

    public static TrailDifficultyResponse from(TrailEdge edge) {
        return new TrailDifficultyResponse(
                edge.getEdgeId(),
                edge.getDistanceM(),
                edge.getSlopePercent(),
                edge.getElevationDiffM(),
                edge.getDifficultyScore(),
                edge.getDifficulty(),
                edge.getNearestSummitId()
        );
    }
}