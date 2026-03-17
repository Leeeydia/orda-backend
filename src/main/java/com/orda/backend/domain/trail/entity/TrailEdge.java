package com.orda.backend.domain.trail.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trail_edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrailEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "edge_id", nullable = false, unique = true)
    private String edgeId;

    @Column(name = "start_node_id", nullable = false)
    private String startNodeId;

    @Column(name = "end_node_id", nullable = false)
    private String endNodeId;

    @Column(name = "distance_m", nullable = false)
    private Double distanceM;

    @Column(name = "elevation_start_m")
    private Double elevationStartM;

    @Column(name = "elevation_end_m")
    private Double elevationEndM;

    @Column(name = "elevation_diff_m")
    private Double elevationDiffM;

    @Column(name = "slope_percent")
    private Double slopePercent;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "difficulty_score")
    private Double difficultyScore;

    @Column(name = "nearest_summit_id")
    private String nearestSummitId;

    @Column(name = "qa_status")
    private String qaStatus;

    public void updateDifficulty(Double difficultyScore, String difficulty) {
        this.difficultyScore = difficultyScore;
        this.difficulty = difficulty;
    }
}