package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.dto.DeviceTokenResponse;
import com.atlas.auth_service.dto.RegisterDeviceTokenRequest;
import com.atlas.auth_service.entity.DeviceTokenEntity;
import com.atlas.auth_service.repository.DeviceTokenRepository;
import com.atlas.auth_service.service.IDeviceTokenService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceTokenServiceImpl implements IDeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenServiceImpl(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Override
    public void registerToken(UUID userId, RegisterDeviceTokenRequest request) {
        Optional<DeviceTokenEntity> existing = deviceTokenRepository
                .findByUserIdAndDeviceOs(userId, request.getDeviceOs());

        DeviceTokenEntity deviceTokenEntityentity;
        if (existing.isPresent()) {
            deviceTokenEntityentity = existing.get();
            deviceTokenEntityentity.setFcmToken(request.getFcmToken());
        } else {
            deviceTokenEntityentity = new DeviceTokenEntity();
            deviceTokenEntityentity.setUserId(userId);
            deviceTokenEntityentity.setFcmToken(request.getFcmToken());
            deviceTokenEntityentity.setDeviceOs(request.getDeviceOs());
        }

        deviceTokenRepository.save(deviceTokenEntityentity);
    }

    @Override
    public List<DeviceTokenResponse> getTokensByUserId(UUID userId) {
        return deviceTokenRepository.findByUserId(userId).stream()
                .map(e -> new DeviceTokenResponse(e.getFcmToken(), e.getDeviceOs()))
                .toList();
    }
}
