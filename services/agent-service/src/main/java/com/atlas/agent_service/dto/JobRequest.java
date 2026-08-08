package com.atlas.agent_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record JobRequest(
        @NotNull UUID projectId,
        @NotBlank String prompt,
        @NotBlank String provider
) {}
