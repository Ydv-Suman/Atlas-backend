package com.atlas.agent_service.llm;

import com.atlas.agent_service.rag.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderHttpContractTest {
    private static final String VECTOR = "[" + "0.1,".repeat(767) + "0.1]";

    @Test
    void providersBuildAbsoluteUrlsWithoutMutatingTheSharedBuilderOrLeakingKeysInUrls() {
        List<ClientRequest> requests = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().baseUrl("https://original.example")
                .exchangeFunction(request -> {
                    requests.add(request);
                    String response = request.url().getPath().contains("batchEmbedContents")
                            ? "{\"embeddings\":[{\"values\":" + VECTOR + "}]}"
                            : request.url().getHost().equals("api.openai.com")
                            ? "{\"choices\":[{\"message\":{\"content\":\"--- a/file\\n+++ b/file\"}}],\"usage\":{}}"
                            : request.url().getHost().equals("api.anthropic.com")
                            ? "{\"content\":[{\"text\":\"--- a/file\\n+++ b/file\"}],\"usage\":{}}"
                            : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"--- a/file\\n+++ b/file\"}]}}],\"usageMetadata\":{}}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(response).build());
                });
        var embedding = new EmbeddingService(builder, "test-index-key");
        var gemini = new GeminiProvider(builder);
        var openai = new OpenAiProvider(builder);
        var claude = new ClaudeProvider(builder);
        assertEquals(768, embedding.embed("sample").length);
        var input = new LlmRequest("Fix file", "file", List.of());
        gemini.generateDiff(input, "test-gemini-key");
        openai.generateDiff(input, "test-openai-key");
        claude.generateDiff(input, "test-claude-key");
        builder.build().get().uri("/check").retrieve().toBodilessEntity().block();

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:batchEmbedContents", requests.get(0).url().toString());
        assertEquals("test-index-key", requests.get(0).headers().getFirst("x-goog-api-key"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", requests.get(1).url().toString());
        assertEquals("test-gemini-key", requests.get(1).headers().getFirst("x-goog-api-key"));
        assertEquals("https://api.openai.com/v1/chat/completions", requests.get(2).url().toString());
        assertEquals("https://api.anthropic.com/v1/messages", requests.get(3).url().toString());
        assertEquals("https://original.example/check", requests.get(4).url().toString());
        requests.forEach(request -> assertNull(request.url().getQuery()));
    }

    @Test
    void indexingFailuresExposeAReasonAndRejectWrongVectorDimensions() {
        var rejected = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.FORBIDDEN).body("private provider details").build()));
        var error = assertThrows(LlmException.class, () -> new EmbeddingService(rejected, "test-key").embed("sample"));
        assertEquals("Code indexing is unavailable. Check the server's Gemini API key and access.", error.getMessage());

        var malformed = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"embeddings\":[{\"values\":[0.1]}]}").build()));
        assertThrows(LlmException.class, () -> new EmbeddingService(malformed, "test-key").embed("sample"));
    }
}
