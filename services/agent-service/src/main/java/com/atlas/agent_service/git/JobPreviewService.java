package com.atlas.agent_service.git;

import com.atlas.agent_service.dto.JobFilePreview;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPreviewService {
    private final AgentJobRepository jobRepository;
    private final WorkspaceCacheService workspaceCache;
    private final WorkspaceFeignClient workspaceClient;
    private final com.atlas.agent_service.runtime.SessionWorkspaceService sessions;

    public List<JobFilePreview> getFiles(UUID jobId, UUID userId) throws Exception {
        var job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!userId.equals(job.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (job.getEventsJson() != null) {
            Path repo = sessions.prepare(userId, job.getProjectId());
            var files = new ArrayList<JobFilePreview>();
            String changed = sessions.gitCommand(repo, "diff", "--name-only", "-z", "--no-ext-diff", "--no-textconv", sessions.base(repo));
            for (String name : changed.split("\0")) if (!name.isBlank()) {
                Path file = sessions.safeFile(repo, name);
                boolean deleted = !Files.exists(file);
                String content = null;
                if (!deleted && Files.size(file) <= 262144) try { content = (String)sessions.file(repo, name).get("content"); } catch (Exception ignored) {}
                files.add(new JobFilePreview(name, content, deleted));
            }
            return files;
        }
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The agent is still working");
        }
        if (job.getDiffOutput() == null || job.getDiffOutput().isBlank()) return List.of();
        String repoUrl = workspaceClient.getRepoUrl(job.getProjectId());
        Path workspace = Path.of(workspaceCache.getWorkspace(job.getProjectId(), repoUrl, userId));
        return preview(workspace, job.getDiffOutput());
    }

    static List<JobFilePreview> preview(Path workspace, String diff) throws Exception {
        Path checkout = Files.createTempDirectory("atlas-preview-");
        Path patch = Files.createTempFile("atlas-preview-", ".patch");
        boolean attached = false;
        try {
            run(workspace, "git", "worktree", "add", "--detach", checkout.toString(), "HEAD");
            attached = true;
            Files.writeString(patch, diff);
            GitPatch.apply(checkout, patch, false);
            run(checkout, "git", "add", "--intent-to-add", "--all");
            String[] changes = run(checkout, "git", "diff", "--name-status", "--no-renames", "-z", "HEAD").split("\0");
            List<JobFilePreview> files = new ArrayList<>();
            Path root = checkout.toRealPath();
            for (int i = 0; i + 1 < changes.length; i += 2) {
                boolean deleted = changes[i].equals("D");
                String path = changes[i + 1];
                Path file = root.resolve(path).normalize();
                if (!file.startsWith(root) || (!deleted && (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)))) {
                    throw new IOException("Cannot preview a file outside the project");
                }
                String content = null;
                if (!deleted && Files.size(file) <= 262144) {
                    try {
                        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
                        if (text.indexOf('\0') < 0) content = text;
                    } catch (CharacterCodingException ignored) { /* Binary files have no text preview. */ }
                }
                files.add(new JobFilePreview(path, content, deleted));
            }
            return files;
        } finally {
            try {
                if (attached) run(workspace, "git", "worktree", "remove", "--force", checkout.toString());
                else Files.deleteIfExists(checkout);
            } finally {
                Files.deleteIfExists(patch);
            }
        }
    }

    private static String run(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("Could not prepare the generated file preview", new IOException(output));
        return output;
    }
}
