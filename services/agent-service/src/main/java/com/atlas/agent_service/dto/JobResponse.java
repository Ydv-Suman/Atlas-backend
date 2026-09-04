package com.atlas.agent_service.dto;

import com.atlas.agent_service.entity.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        Long projectId,
        JobStatus status,
        String diffOutput,
        String errorMessage,
        Integer creditsConsumed,
        Instant createdAt,
        Instant completedAt
) {}
