package com.atlas.agent_service.rag;

import com.atlas.agent_service.entity.RepoEmbedding;
import com.atlas.agent_service.repository.RepoEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int TOP_K = 10;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", ".gradle",
            ".idea", ".vscode", "__pycache__", "dist", "vendor"
    );
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx",
            ".go", ".rs", ".rb", ".php", ".swift", ".c", ".cpp", ".h",
            ".cs", ".scala", ".yml", ".yaml", ".json", ".xml",
            ".sql", ".sh", ".md", ".toml", ".gradle"
    );

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RepoEmbeddingRepository embeddingRepository;

    @Transactional
    public void indexRepository(Long projectId, String workspacePath, String commitHash) {
        log.info("Indexing repository for project {} at {}", projectId, workspacePath);
        Path root = Path.of(workspacePath);

        List<Path> sourceFiles = collectSourceFiles(root);
        log.info("Found {} source files to index", sourceFiles.size());

        for (Path file : sourceFiles) {
            String relativePath = root.relativize(file).toString();
            try {
                String content = Files.readString(file);
                indexFile(projectId, relativePath, content, commitHash);
            } catch (IOException e) {
                log.warn("Failed to read file {}: {}", relativePath, e.getMessage());
            }
        }

        log.info("Indexing complete for project {}", projectId);
    }

    @Transactional
    public void invalidateFiles(Long projectId, List<String> changedFiles) {
        for (String filePath : changedFiles) {
            embeddingRepository.deleteByProjectIdAndFilePath(projectId, filePath);
            log.debug("Invalidated embeddings for {}", filePath);
        }
    }

    @Transactional
    public void reindexFiles(Long projectId, String workspacePath,
                             List<String> changedFiles, String commitHash) {
        invalidateFiles(projectId, changedFiles);

        Path root = Path.of(workspacePath);
        for (String filePath : changedFiles) {
            Path file = root.resolve(filePath);
            if (!Files.exists(file)) continue;

            try {
                String content = Files.readString(file);
                indexFile(projectId, filePath, content, commitHash);
            } catch (IOException e) {
                log.warn("Failed to reindex file {}: {}", filePath, e.getMessage());
            }
        }
    }

    public List<String> retrieve(Long projectId, String query) {
        float[] queryEmbedding = embeddingService.embed(query);
        String vectorString = embeddingService.toVectorString(queryEmbedding);

        List<RepoEmbedding> results = embeddingRepository.findSimilar(
                projectId, vectorString, TOP_K);

        return results.stream()
                .map(RepoEmbedding::getContent)
                .toList();
    }

    private void indexFile(Long projectId, String filePath, String content, String commitHash) {
        embeddingRepository.deleteByProjectIdAndFilePath(projectId, filePath);

        List<ChunkingService.Chunk> chunks = chunkingService.chunkFile(filePath, content);
        if (chunks.isEmpty()) return;

        List<String> chunkTexts = chunks.stream()
                .map(ChunkingService.Chunk::content)
                .toList();

        List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

        for (int i = 0; i < chunks.size(); i++) {
            ChunkingService.Chunk chunk = chunks.get(i);
            RepoEmbedding entity = new RepoEmbedding();
            entity.setProjectId(projectId);
            entity.setFilePath(chunk.filePath());
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setContent(chunk.content());
            entity.setEmbedding(embeddings.get(i));
            entity.setCommitHash(commitHash);
            embeddingRepository.save(entity);
        }

        log.debug("Indexed {} chunks for {}", chunks.size(), filePath);
    }

    private List<Path> collectSourceFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(root, p))
                    .filter(p -> isCodeFile(p))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to walk directory {}: {}", root, e.getMessage());
            return List.of();
        }
    }

    private boolean isSkipped(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (SKIP_DIRS.contains(relative.getName(i).toString())) return true;
        }
        return false;
    }

    private boolean isCodeFile(Path file) {
        String name = file.getFileName().toString();
        return CODE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
