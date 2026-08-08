package com.atlas.agent_service.dto;

import java.time.Instant;
import java.util.UUID;

public record KeyResponse(
        UUID id,
        String provider,
        String keyHint,
        Instant createdAt
) {}
