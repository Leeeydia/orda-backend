package com.orda.backend.domain.hiking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HikingStartRequest {

    @NotNull(message = "userId는 필수입니다.")
    private Long userId;

    private Double latitude;
    private Double longitude;
}