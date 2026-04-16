package com.orda.backend.domain.mountain.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Top100MountainResponse {
    private String name;
    private String location;
    private Double height;
    private String difficulty;
    private String feature;
    private Double latitude;
    private Double longitude;
    private List<String> edgeIds;
}