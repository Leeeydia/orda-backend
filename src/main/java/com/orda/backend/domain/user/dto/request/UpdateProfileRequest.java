package com.orda.backend.domain.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateProfileRequest {

    @Size(max = 50)
    private String nickname;

    private String profileImageUrl;
}