package com.atlas.agent_service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiProvider implements LlmProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o";
    private static final double TEMPERATURE = 0.2;

    private final WebClient webClient;

    public OpenAiProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.clone().baseUrl(API_URL).build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public LlmResponse generateDiff(LlmRequest request, String apiKey) {
        String userMessage = buildUserMessage(request);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "temperature", TEMPERATURE,
                "max_tokens", 4096,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        Map<?, ?> response = callApi(body, apiKey);
        String diff = extractDiff(response);
        int tokens = extractTokensUsed(response);

        return AgentConversation.parse(diff, tokens, name());
    }

    private Map<?, ?> callApi(Map<String, Object> body, String apiKey) {
        return webClient.post()
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    private String extractDiff(Map<?, ?> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        return message != null ? (String) message.get("content") : "";
    }

    @SuppressWarnings("unchecked")
    private int extractTokensUsed(Map<?, ?> response) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage == null) return 0;
        return ((Number) usage.getOrDefault("total_tokens", 0)).intValue();
    }

    private String buildUserMessage(LlmRequest request) {
        var sb = new StringBuilder();
        sb.append("## File Tree\n```\n").append(request.fileTree()).append("\n```\n\n");

        if (request.contextChunks() != null && !request.contextChunks().isEmpty()) {
            sb.append("## Relevant Code\n");
            for (String chunk : request.contextChunks()) {
                sb.append("```\n").append(chunk).append("\n```\n\n");
            }
        }

        sb.append("## Task\n").append(request.prompt());
        return sb.toString();
    }

    private String systemPrompt() {
        return AgentConversation.SYSTEM;
    }
}
