package com.atlas.agent_service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private static final String MODEL = "text-embedding-004";
    private static final int DIMENSIONS = 768;
    private static final int BATCH_SIZE = 100;

    private final WebClient webClient;
    private final String apiKey;

    public EmbeddingService(WebClient.Builder webClientBuilder,
                            @Value("${atlas.gemini.api-key}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/")
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
                            "content", Map.of("parts", List.of(Map.of("text", t)))
                    ))
                    .toList();

            Map<String, Object> body = Map.of("requests", requests);

            Map<?, ?> response = webClient.post()
                    .uri(MODEL + ":batchEmbedContents?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> embeddings =
                    (List<Map<String, Object>>) response.get("embeddings");

            for (Map<String, Object> emb : embeddings) {
                List<Number> values = (List<Number>) emb.get("values");
                float[] vector = new float[DIMENSIONS];
                for (int j = 0; j < Math.min(values.size(), DIMENSIONS); j++) {
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
