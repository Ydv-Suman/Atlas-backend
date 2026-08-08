package com.atlas.agent_service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiProvider implements LlmProvider {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL = "gemini-2.5-flash";
    private static final double TEMPERATURE = 0.2;

    private final WebClient webClient;

    public GeminiProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(API_URL).build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public LlmResponse generateDiff(LlmRequest request, String apiKey) {
        String userMessage = buildUserMessage(request);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userMessage)))
                ),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt()))
                ),
                "generationConfig", Map.of(
                        "temperature", TEMPERATURE,
                        "maxOutputTokens", 4096
                )
        );

        Map<?, ?> response = callApi(body, apiKey);
        String diff = extractDiff(response);
        int tokens = extractTokensUsed(response);

        if (!isValidDiff(diff)) {
            log.warn("Malformed diff from Gemini, retrying once");
            response = callApi(body, apiKey);
            diff = extractDiff(response);
            tokens += extractTokensUsed(response);

            if (!isValidDiff(diff)) {
                throw new LlmException("Gemini returned malformed diff after retry");
            }
        }

        return new LlmResponse(diff, tokens, name());
    }

    private Map<?, ?> callApi(Map<String, Object> body, String apiKey) {
        return webClient.post()
                .uri(MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    private String extractDiff(Map<?, ?> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) return "";

        Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
        if (content == null) return "";

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return "";

        return (String) parts.getFirst().get("text");
    }

    @SuppressWarnings("unchecked")
    private int extractTokensUsed(Map<?, ?> response) {
        Map<String, Object> metadata = (Map<String, Object>) response.get("usageMetadata");
        if (metadata == null) return 0;
        int prompt = ((Number) metadata.getOrDefault("promptTokenCount", 0)).intValue();
        int output = ((Number) metadata.getOrDefault("candidatesTokenCount", 0)).intValue();
        return prompt + output;
    }

    private boolean isValidDiff(String diff) {
        return diff != null && !diff.isBlank()
                && (diff.contains("---") || diff.contains("diff --git"));
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
        return """
                You are a senior software engineer. You receive a codebase file tree, \
                relevant code snippets, and a task description.

                Your ONLY output must be a valid unified diff that can be applied with `git apply`. \
                Do not include any explanation, commentary, or markdown fencing. \
                Output the raw diff and nothing else.

                Rules:
                - Use correct file paths from the file tree.
                - Include proper --- and +++ headers with a/ b/ prefixes.
                - Each hunk must have a valid @@ line.
                - Do not modify files unrelated to the task.
                - If creating a new file, use /dev/null as the --- path.""";
    }
}
