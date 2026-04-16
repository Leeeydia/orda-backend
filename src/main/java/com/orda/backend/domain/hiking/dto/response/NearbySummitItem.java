package com.orda.backend.domain.hiking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NearbySummitItem {

    private String summitId;
    private String summitName;
    private double latitude;
    private double longitude;
    private Double elevationM;
}