package com.atlas.agent_service.llm;

public record LlmResponse(
        String diff,
        int tokensUsed,
        String provider
) {}
