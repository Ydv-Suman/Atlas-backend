package com.atlas.agent_service.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveKeyRequest(
        @NotBlank String provider,
        @NotBlank String apiKey
) {}
