package com.atlas.agent_service.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePrRequest(
        @NotBlank String branchName,
        @NotBlank String title,
        @NotBlank String description
) {}
