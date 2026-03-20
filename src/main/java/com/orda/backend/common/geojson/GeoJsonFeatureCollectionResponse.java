package com.orda.backend.common.geojson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoJsonFeatureCollectionResponse {

    private String type = "FeatureCollection";
    private List<GeoJsonFeatureResponse> features = new ArrayList<>();

    public static GeoJsonFeatureCollectionResponse of(List<GeoJsonFeatureResponse> features) {
        GeoJsonFeatureCollectionResponse response = new GeoJsonFeatureCollectionResponse();
        response.setFeatures(features);
        return response;
    }

    public void addFeature(GeoJsonFeatureResponse feature) {
        this.features.add(feature);
    }
}