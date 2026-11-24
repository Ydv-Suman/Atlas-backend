package com.atlas.agent_service.llm;

public record LlmResponse(
        String diff,
        int tokensUsed,
        String provider,
        String message
) {
    public LlmResponse(String diff, int tokensUsed, String provider) {
        this(diff, tokensUsed, provider, "Changes ready for review.");
    }

    public LlmResponse {
        if (diff != null && !diff.isEmpty()) {
            diff = diff.replace("\r\n", "\n");
            if (diff.startsWith("```")) {
                int start = diff.indexOf('\n');
                int end = diff.lastIndexOf("\n```");
                if (start >= 0 && end > start) diff = diff.substring(start + 1, end + 1);
            }
            if (!diff.endsWith("\n")) diff += "\n";
        }
    }
}
