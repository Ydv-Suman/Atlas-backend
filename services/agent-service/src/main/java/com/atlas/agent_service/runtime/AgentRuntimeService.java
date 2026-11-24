package com.atlas.agent_service.runtime;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.llm.LlmException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AgentRuntimeService {
    private final AgentEventHub events;
    private final SessionWorkspaceService sessions;
    @Value("${atlas.runtime.bridge:/app/runtime/bridge.mjs}") private String bridge;
    private final JsonMapper json = JsonMapper.builder().build();

    public String run(AgentJob job, Path repo, String apiKey) throws Exception {
        var builder = new ProcessBuilder(sessions.isolatedCommand(job.getProjectId(), List.of("node", bridge))).directory(repo.toFile());
        var env = builder.environment(); env.clear();
        env.put("PATH", "/opt/java/openjdk/bin:/usr/local/bin:/usr/bin:/bin");
        env.put("JAVA_HOME", "/opt/java/openjdk");
        env.put("JAVA_TOOL_OPTIONS", "-Duser.home=" + repo.getParent().resolve("home"));
        env.put("HOME", repo.getParent().resolve("home").toString());
        env.put("TMPDIR", repo.getParent().resolve("home/tmp").toString());
        env.put("LANG", "C.UTF-8");
        Process process = builder.start();
        CompletableFuture.runAsync(() -> { try { process.getErrorStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) {} });
        var output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        java.util.function.Consumer<Map<String,Object>> send = message -> {
            synchronized (output) { try { output.write(json.writeValueAsString(message)); output.newLine(); output.flush(); }
                catch (IOException e) { throw new IllegalStateException("The agent connection closed.", e); } }
        };
        AtomicReference<String> response = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>();
        try {
            send.accept(Map.of("provider", job.getAgentProvider(), "prompt", job.getPrompt(), "apiKey", apiKey,
                "cwd", repo.toString(), "stateDir", repo.getParent().resolve("state").resolve(job.getAgentProvider()).toString()));
            events.attach(job, send);
            var reader = CompletableFuture.runAsync(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        if (line.length() > 1_000_000) throw new IOException("Agent output exceeded its limit.");
                        @SuppressWarnings("unchecked") Map<String,Object> event = json.readValue(line, Map.class);
                        if (event.get("type").equals("result")) response.set(String.valueOf(event.getOrDefault("text", "")));
                        if (event.get("type").equals("error")) error.set(String.valueOf(event.get("text")));
                        events.publish(job, event);
                    }
                } catch (Exception e) { throw new CompletionException(e); }
            });
            try { reader.get(20, TimeUnit.MINUTES); }
            catch (TimeoutException e) { throw new LlmException("The agent session timed out. Your files were saved; send another prompt to continue."); }
            if (events.cancelled(job)) throw new CancellationException("Stopped by you.");
            if (error.get() != null) throw new LlmException(error.get());
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new LlmException("The agent runtime stopped unexpectedly. Your files were saved.");
            return response.get();
        } finally {
            process.descendants().forEach(child -> child.destroy());
            process.destroy();
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
