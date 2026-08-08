package com.atlas.agent_service.llm;

public interface LlmProvider {

    String name();

    LlmResponse generateDiff(LlmRequest request, String apiKey);
}
