package com.orda.backend.domain.hiking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "summit_points")
@Getter
@NoArgsConstructor
public class SummitPoint {

    @Id
    @Column(name = "summit_id")
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;
}