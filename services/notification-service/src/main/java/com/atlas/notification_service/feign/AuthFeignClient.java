package com.atlas.notification_service.feign;

import com.atlas.notification_service.dto.DeviceTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", url = "${feign.auth-service.url:}", path = "/api/auth/internal")
public interface AuthFeignClient {

    @GetMapping("/device-token/{userId}")
    List<DeviceTokenResponse> getDeviceTokens(@PathVariable UUID userId);
}