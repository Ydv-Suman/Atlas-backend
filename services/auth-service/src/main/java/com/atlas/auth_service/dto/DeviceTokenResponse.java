package com.atlas.auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeviceTokenResponse {

    private String fcmToken;

    private String deviceOs;
}
