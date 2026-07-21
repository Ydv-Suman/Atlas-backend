package com.atlas.agent_service.dto;

import jakarta.validation.constraints.NotBlank;

public record PushRequest(
        @NotBlank String branchName,
        @NotBlank String commitMessage
) {}
