package com.atlas.agent_service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import com.atlas.agent_service.llm.LlmException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL = "gemini-embedding-2";
    private static final int DIMENSIONS = 768;
    private static final int BATCH_SIZE = 100;

    private final WebClient webClient;
    private final String apiKey;

    public EmbeddingService(WebClient.Builder webClientBuilder,
                            @Value("${atlas.gemini.api-key}") String apiKey) {
        this.webClient = webClientBuilder.clone()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.apiKey = apiKey;
    }

    public float[] embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));

            List<Map<String, Object>> requests = batch.stream()
                    .map(t -> Map.<String, Object>of(
                            "model", "models/" + MODEL,
                            "outputDimensionality", DIMENSIONS,
                            "content", Map.of("parts", List.of(Map.of("text", t)))
                    ))
                    .toList();

            Map<String, Object> body = Map.of("requests", requests);

            Map<?, ?> response;
            try {
                response = webClient.post()
                    .uri("/v1beta/models/" + MODEL + ":batchEmbedContents")
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            } catch (WebClientResponseException e) {
                String reason = switch (e.getStatusCode().value()) {
                    case 400, 401, 403 -> "Code indexing is unavailable. Check the server's Gemini API key and access.";
                    case 404 -> "The code indexing model is unavailable.";
                    case 429 -> "Code indexing is rate limited. Try again shortly.";
                    default -> "The code indexing service is unavailable. Try again shortly.";
                };
                throw new LlmException(reason, e);
            } catch (WebClientRequestException e) {
                throw new LlmException("Couldn't connect to the code indexing service.", e);
            }

            List<Map<String, Object>> embeddings =
                    response == null ? null : (List<Map<String, Object>>) response.get("embeddings");
            if (embeddings == null || embeddings.size() != batch.size()) {
                throw new LlmException("Code indexing returned incomplete results. Try again.");
            }

            for (Map<String, Object> emb : embeddings) {
                List<Number> values = (List<Number>) emb.get("values");
                if (values == null || values.size() != DIMENSIONS) {
                    throw new LlmException("Code indexing returned an incompatible embedding size.");
                }
                float[] vector = new float[DIMENSIONS];
                for (int j = 0; j < DIMENSIONS; j++) {
                    vector[j] = values.get(j).floatValue();
                }
                allEmbeddings.add(vector);
            }
        }

        return allEmbeddings;
    }

    public String toVectorString(float[] embedding) {
        return Arrays.toString(embedding);
    }
}
