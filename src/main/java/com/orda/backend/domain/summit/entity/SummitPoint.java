package com.orda.backend.domain.summit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

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

    @Column(name = "source")
    private String source;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;

    @Column(name = "geom", nullable = false, columnDefinition = "GEOMETRY(Point, 4326)")
    private Point geom;
}