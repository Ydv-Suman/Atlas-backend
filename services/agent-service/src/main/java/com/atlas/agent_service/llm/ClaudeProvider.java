package com.atlas.agent_service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ClaudeProvider implements LlmProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final double TEMPERATURE = 0.2;

    private final WebClient webClient;

    public ClaudeProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(API_URL).build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public LlmResponse generateDiff(LlmRequest request, String apiKey) {
        String userMessage = buildUserMessage(request);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 4096,
                "temperature", TEMPERATURE,
                "system", systemPrompt(),
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        Map<?, ?> response = callApi(body, apiKey);
        String diff = extractDiff(response);
        int tokens = extractTokensUsed(response);

        if (!isValidDiff(diff)) {
            log.warn("Malformed diff from Claude, retrying once");
            response = callApi(body, apiKey);
            diff = extractDiff(response);
            tokens += extractTokensUsed(response);

            if (!isValidDiff(diff)) {
                throw new LlmException("Claude returned malformed diff after retry");
            }
        }

        return new LlmResponse(diff, tokens, name());
    }

    private Map<?, ?> callApi(Map<String, Object> body, String apiKey) {
        return webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    private String extractDiff(Map<?, ?> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return "";
        }
        return (String) content.getFirst().get("text");
    }

    @SuppressWarnings("unchecked")
    private int extractTokensUsed(Map<?, ?> response) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage == null) return 0;
        int input = ((Number) usage.getOrDefault("input_tokens", 0)).intValue();
        int output = ((Number) usage.getOrDefault("output_tokens", 0)).intValue();
        return input + output;
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
