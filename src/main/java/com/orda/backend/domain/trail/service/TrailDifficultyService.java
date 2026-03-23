package com.orda.backend.domain.trail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orda.backend.common.geojson.GeoJsonFeatureCollectionResponse;
import com.orda.backend.common.geojson.GeoJsonFeatureResponse;
import com.orda.backend.common.geojson.GeoJsonGeometryResponse;
import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.entity.TrailEdge;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrailDifficultyService {

    private final TrailEdgeRepository trailEdgeRepository;
    private final ObjectMapper objectMapper;

    public TrailDifficultyResponse getByEdgeId(String edgeId) {
        TrailEdge edge = trailEdgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 edgeId: " + edgeId));
        return TrailDifficultyResponse.from(edge);
    }

    public List<TrailDifficultyResponse> getBySummitId(String summitId) {
        return trailEdgeRepository.findByNearestSummitId(summitId)
                .stream()
                .map(TrailDifficultyResponse::from)
                .toList();
    }

    public List<TrailDifficultyResponse> getAll() {
        return trailEdgeRepository.findAll()
                .stream()
                .map(TrailDifficultyResponse::from)
                .toList();
    }

    public GeoJsonFeatureCollectionResponse getDifficultyMap() {
        List<Object[]> rows = trailEdgeRepository.findAllEdgesWithGeom();
        return buildFeatureCollection(rows);
    }

    public GeoJsonFeatureCollectionResponse getDifficultyMapBySummit(String summitId) {
        List<Object[]> rows = trailEdgeRepository.findEdgesWithGeomBySummitId(summitId);
        return buildFeatureCollection(rows);
    }

    private GeoJsonFeatureCollectionResponse buildFeatureCollection(List<Object[]> rows) {
        List<GeoJsonFeatureResponse> features = new ArrayList<>();

        for (Object[] row : rows) {
            String edgeId = (String) row[0];
            String difficulty = (String) row[1];
            Double difficultyScore = row[2] != null ? ((Number) row[2]).doubleValue() : null;
            String geomJson = (String) row[3];

            try {
                Map<String, Object> geomMap = objectMapper.readValue(geomJson, Map.class);
                List<List<Double>> coordinates = (List<List<Double>>) geomMap.get("coordinates");

                GeoJsonGeometryResponse geometry = GeoJsonGeometryResponse.lineString(coordinates);

                Map<String, Object> properties = Map.of(
                        "edgeId", edgeId,
                        "difficulty", difficulty != null ? difficulty : "",
                        "difficultyScore", difficultyScore != null ? difficultyScore : 0.0
                );

                features.add(GeoJsonFeatureResponse.of(geometry, properties));

            } catch (Exception e) {
                continue;
            }
        }

        return GeoJsonFeatureCollectionResponse.of(features);
    }
}