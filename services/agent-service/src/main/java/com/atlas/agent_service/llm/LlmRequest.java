package com.atlas.agent_service.llm;

import java.util.List;

public record LlmRequest(
        String prompt,
        String fileTree,
        List<String> contextChunks
) {}
