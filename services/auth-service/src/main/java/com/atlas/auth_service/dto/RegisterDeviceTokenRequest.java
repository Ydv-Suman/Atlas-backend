package com.atlas.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDeviceTokenRequest {

    @NotBlank
    private String fcmToken;

    @NotBlank
    @Pattern(regexp = "ios|android")
    private String deviceOs;
}
