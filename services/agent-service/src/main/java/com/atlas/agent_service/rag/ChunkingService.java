package com.atlas.agent_service.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int MAX_TOKENS = 500;
    private static final int OVERLAP_LINES = 3;
    private static final double CHARS_PER_TOKEN = 4.0;

    public List<Chunk> chunkFile(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String[] lines = content.split("\n");
        List<Chunk> chunks = new ArrayList<>();
        int maxChars = (int) (MAX_TOKENS * CHARS_PER_TOKEN);

        int start = 0;
        int chunkIndex = 0;

        while (start < lines.length) {
            var sb = new StringBuilder();
            int end = start;

            while (end < lines.length) {
                String candidate = sb.isEmpty() ? lines[end] : sb + "\n" + lines[end];
                if (candidate.length() > maxChars && !sb.isEmpty()) break;
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(lines[end]);
                end++;
            }

            String chunkContent = filePath + "\n---\n" + sb;
            chunks.add(new Chunk(filePath, chunkIndex, chunkContent));
            chunkIndex++;

            start = Math.max(start + 1, end - OVERLAP_LINES);
        }

        return chunks;
    }

    public record Chunk(String filePath, int chunkIndex, String content) {}
}
