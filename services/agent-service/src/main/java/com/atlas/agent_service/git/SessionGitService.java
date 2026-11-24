package com.atlas.agent_service.git;

import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.agent_service.runtime.SessionWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SessionGitService {
    private final SessionWorkspaceService sessions;
    private final AgentJobRepository jobs;
    private final AuthFeignClient auth;
    private final WorkspaceFeignClient workspace;
    @Value("${atlas.runtime.bridge:/app/runtime/bridge.mjs}") private String bridge;
    private static final Set<String> READS = Set.of("status", "diff", "history");
    private static final Set<String> WRITES = Set.of("commit", "create-branch", "switch", "fetch", "pull", "push", "merge", "rebase", "continue", "abort");
    private static final Set<String> NETWORK = Set.of("fetch", "pull", "push");
    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    public Object execute(UUID user, Long project, String operation, Map<String, Object> arguments) throws Exception {
        if (!READS.contains(operation) && !WRITES.contains(operation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown Git action.");
        synchronized (sessions.lock(user, project)) {
            Path repo = sessions.prepare(user, project);
            if (WRITES.contains(operation) && jobs.existsByProjectIdAndUserIdAndStatusIn(project, user, List.of(JobStatus.PENDING, JobStatus.RUNNING)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop the agent before changing Git branches or commits.");
            var request = new HashMap<String,Object>();
            for (String key : List.of("target", "message", "filePath", "expectedRevision", "expectedHead")) {
                Object value = arguments.get(key);
                if (value != null) {
                    if (!(value instanceof String text) || text.length() > 4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Git request.");
                    request.put(key, value);
                }
            }
            request.put("root", repo.toString()); request.put("operation", operation);
            if (NETWORK.contains(operation)) {
                String remote = workspace.getRepoUrl(project);
                if (remote == null || !remote.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?"))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the project's GitHub repository can be synced.");
                request.put("repoUrl", remote); request.put("token", auth.getGithubToken(user));
            }
            if (Set.of("commit", "merge", "rebase", "continue").contains(operation)) request.put("identity", auth.getUserIdentity(user));
            var builder = new ProcessBuilder(sessions.isolatedCommand(project, List.of("node", Path.of(bridge).getParent().resolve("git.mjs").toString()))).directory(repo.toFile());
            builder.environment().clear();
            builder.environment().put("PATH", System.getProperty("os.name").startsWith("Mac") ? System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin") : "/usr/local/bin:/usr/bin:/bin");
            var process = builder.start();
            try (var input = process.getOutputStream()) { input.write(json.writeValueAsBytes(request)); }
            var output = CompletableFuture.supplyAsync(() -> {
                try { return process.getInputStream().readNBytes(8_000_001); }
                catch (Exception error) { throw new CompletionException(error); }
            });
            if (!process.waitFor(110, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Git timed out. Refresh before retrying."); }
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Git is temporarily unavailable. Please try again.");
            if (bytes.length > 8_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Git output is too large to display.");
            var response = json.readTree(bytes);
            if (!response.path("ok").asBoolean()) throw new ResponseStatusException(HttpStatus.valueOf(response.path("status").asInt()), response.path("message").asString());
            Object result = json.convertValue(response.get("data"), Object.class);
            if (WRITES.contains(operation) && result instanceof Map<?,?> data && data.get("status") instanceof Map<?,?> state
                    && state.get("operation") == null && state.get("head") instanceof String head && head.matches("[a-fA-F0-9]{40,64}")) {
                Path base = sessions.directory(user, project).getParent().getParent().resolve(".metadata").resolve(user.toString()).resolve(project.toString()).resolve("base");
                Files.writeString(base, head);
            }
            return result;
        }
    }
}
