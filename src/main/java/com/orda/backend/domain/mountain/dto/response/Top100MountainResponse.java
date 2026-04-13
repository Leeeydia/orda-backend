package com.orda.backend.domain.mountain.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Top100MountainResponse {
    private String name;
    private String location;
    private String height;
    private String difficulty;
    private String feature;
    private String latitude;
    private String longitude;
}