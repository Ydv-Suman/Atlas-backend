package com.atlas.auth_service.repository;

import com.atlas.auth_service.entity.DeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, UUID> {

    Optional<DeviceTokenEntity> findByUserIdAndDeviceOs(UUID userId, String deviceOs);

    List<DeviceTokenEntity> findByUserId(UUID userId);
}
