package com.orda.backend.common.geojson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoJsonGeometryResponse {

    private String type;
    private Object coordinates;

    public static GeoJsonGeometryResponse point(double longitude, double latitude) {
        return new GeoJsonGeometryResponse("Point", List.of(longitude, latitude));
    }

    public static GeoJsonGeometryResponse lineString(List<List<Double>> coordinates) {
        return new GeoJsonGeometryResponse("LineString", coordinates);
    }
}
