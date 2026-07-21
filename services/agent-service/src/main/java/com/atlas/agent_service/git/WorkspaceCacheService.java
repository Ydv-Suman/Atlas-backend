package com.atlas.agent_service.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceCacheService {

    private static final Path BASE_DIR = Path.of("/tmp/atlas/workspaces");
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(30);

    private final GitService gitService;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public String getWorkspace(UUID projectId, String repoUrl, UUID userId) throws Exception {
        ReentrantLock lock = locks.computeIfAbsent(projectId, k -> new ReentrantLock());

        if (!lock.tryLock(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for workspace lock on project " + projectId);
        }

        try {
            Path workspacePath = BASE_DIR.resolve(projectId.toString());
            String path = workspacePath.toString();

            if (Files.exists(workspacePath) && isFresh(workspacePath)) {
                log.info("Workspace fresh, syncing: {}", path);
                gitService.syncWorkspace(userId, path);
                return path;
            }

            if (Files.exists(workspacePath)) {
                log.info("Workspace stale, deleting: {}", path);
                deleteDirectory(workspacePath);
            }

            log.info("Cloning workspace: {} → {}", repoUrl, path);
            gitService.cloneRepo(userId, repoUrl, path);
            return path;
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedRate = 600_000)
    public void evictStaleWorkspaces() {
        if (!Files.exists(BASE_DIR)) {
            return;
        }

        try (Stream<Path> dirs = Files.list(BASE_DIR)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    UUID projectId = UUID.fromString(dir.getFileName().toString());
                    ReentrantLock lock = locks.get(projectId);

                    if (lock != null && lock.isLocked()) {
                        return;
                    }

                    if (!isFresh(dir)) {
                        deleteDirectory(dir);
                        locks.remove(projectId);
                        log.info("Evicted stale workspace: {}", dir);
                    }
                } catch (IllegalArgumentException e) {
                    // directory name not a UUID, skip
                } catch (IOException e) {
                    log.warn("Failed to evict workspace: {}", dir, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to scan workspace directory", e);
        }
    }

    private boolean isFresh(Path dir) {
        try {
            Instant lastModified = Files.getLastModifiedTime(dir).toInstant();
            return Duration.between(lastModified, Instant.now()).compareTo(FRESH_WINDOW) < 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}