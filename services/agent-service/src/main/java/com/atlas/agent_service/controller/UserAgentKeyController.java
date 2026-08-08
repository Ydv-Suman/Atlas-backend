package com.atlas.agent_service.controller;

import com.atlas.agent_service.dto.KeyResponse;
import com.atlas.agent_service.dto.SaveKeyRequest;
import com.atlas.agent_service.service.UserAgentKeyService;
import com.atlas.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent/keys")
@RequiredArgsConstructor
public class UserAgentKeyController {

    private final UserAgentKeyService keyService;

    @PostMapping
    public ResponseEntity<ApiResponse<KeyResponse>> saveKey(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SaveKeyRequest request) {

        KeyResponse response = keyService.saveKey(userId, request);
        return ResponseEntity.ok(ApiResponse.success("200", "API key saved", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KeyResponse>>> listKeys(
            @RequestHeader("X-User-Id") UUID userId) {

        List<KeyResponse> responses = keyService.listKeys(userId);
        return ResponseEntity.ok(ApiResponse.success("200", "API keys", responses));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String provider) {

        keyService.deleteKey(userId, provider);
        return ResponseEntity.ok(ApiResponse.success("200", "API key deleted"));
    }
}
