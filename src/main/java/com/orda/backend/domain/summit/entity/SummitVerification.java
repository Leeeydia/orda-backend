package com.orda.backend.domain.summit.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "summit_verifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "summit_id"})
)
@Getter
@NoArgsConstructor
public class SummitVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "summit_id", nullable = false)
    private String summitId;

    @Column(name = "distance_to_summit_m", nullable = false)
    private Double distanceToSummitM;

    @Column(name = "verification_method", nullable = false, length = 30)
    private String verificationMethod;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "geom", nullable = false, columnDefinition = "GEOMETRY(Point, 4326)")
    private Point geom;

    @Builder
    public SummitVerification(Long sessionId, String summitId, Double distanceToSummitM,
                              String verificationMethod, String photoPath, Point geom) {
        this.sessionId = sessionId;
        this.summitId = summitId;
        this.distanceToSummitM = distanceToSummitM;
        this.verificationMethod = verificationMethod != null ? verificationMethod : "gps";
        this.photoPath = photoPath;
        this.verifiedAt = LocalDateTime.now();
        this.geom = geom;
    }
}