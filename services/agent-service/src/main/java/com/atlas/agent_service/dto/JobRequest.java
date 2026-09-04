package com.atlas.agent_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobRequest(
        @NotNull Long projectId,
        @NotBlank String prompt,
        @NotBlank String provider
) {}
