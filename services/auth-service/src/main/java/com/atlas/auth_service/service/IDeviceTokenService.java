package com.atlas.auth_service.service;

import com.atlas.auth_service.dto.DeviceTokenResponse;
import com.atlas.auth_service.dto.RegisterDeviceTokenRequest;

import java.util.List;
import java.util.UUID;

public interface IDeviceTokenService {

    void registerToken(UUID userId, RegisterDeviceTokenRequest request);

    List<DeviceTokenResponse> getTokensByUserId(UUID userId);
}
