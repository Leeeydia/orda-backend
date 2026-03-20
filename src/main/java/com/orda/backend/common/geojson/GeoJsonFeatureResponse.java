package com.orda.backend.common.geojson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoJsonFeatureResponse {

    private String type = "Feature";
    private GeoJsonGeometryResponse geometry;
    private Map<String, Object> properties = new HashMap<>();

    public static GeoJsonFeatureResponse of(GeoJsonGeometryResponse geometry, Map<String, Object> properties) {
        GeoJsonFeatureResponse response = new GeoJsonFeatureResponse();
        response.setGeometry(geometry);
        response.setProperties(properties);
        return response;
    }
}