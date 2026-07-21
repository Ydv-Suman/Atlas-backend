package com.atlas.auth_service.controller;

import com.atlas.auth_service.dto.DeviceTokenResponse;
import com.atlas.auth_service.dto.RegisterDeviceTokenRequest;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.service.IDeviceTokenService;
import com.atlas.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeviceTokenController {

    private final IDeviceTokenService deviceTokenService;
    private final AtlasUserRespsitory userRepository;

    @PostMapping(value = "/auth/device-token", version = "1.0")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            Authentication authentication,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        AtlasUsers user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        deviceTokenService.registerToken(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("200", "Device token registered"));
    }

    @GetMapping("/auth/internal/device-token/{userId}")
    public ResponseEntity<List<DeviceTokenResponse>> getTokensByUserId(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(deviceTokenService.getTokensByUserId(userId));
    }

    @GetMapping("/auth/internal/user-identity/{userId}")
    public ResponseEntity<Map<String, String>> getUserIdentity(@PathVariable UUID userId) {
        AtlasUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of(
                "name", user.getFirstName() + " " + user.getLastName(),
                "email", user.getEmail()
        ));
    }
}
