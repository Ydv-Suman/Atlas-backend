package com.atlas.agent_service.runtime;

import com.atlas.agent_service.git.GitService;
import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionWorkspaceServiceTest {
    @TempDir Path temporary;
    @Test
    void unpublishedFilesSurviveReopeningAndStaleSavesCannotOverwriteThem() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source"));
        git(source, "init"); git(source, "config", "user.name", "Atlas Test"); git(source, "config", "user.email", "test@example.invalid");
        Files.writeString(source.resolve("hello.py"), "print('hello')\n");
        git(source, "add", "."); git(source, "commit", "-m", "initial");
        UUID user = UUID.randomUUID();
        var client = mock(WorkspaceFeignClient.class);
        when(client.getProjectDetails(7L)).thenReturn(Map.of("userId", user.toString()));
        when(client.getRepoUrl(7L)).thenReturn(source.toUri().toString());
        var auth = mock(AuthFeignClient.class); when(auth.getGithubToken(user)).thenReturn("test");
        var jobs = mock(AgentJobRepository.class); when(jobs.findByProjectIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        var sessions = new SessionWorkspaceService(new GitService(auth, WebClient.builder()), auth, client, jobs);
        ReflectionTestUtils.setField(sessions, "workspaceRoot", temporary.resolve("sessions").toString());
        Path repo = sessions.prepare(user, 7L);
        var original = sessions.file(repo, "hello.py");
        sessions.save(repo, "hello.py", "print('edited')\n", (String)original.get("etag"));
        Files.createDirectories(repo.resolve("new-folder"));
        Files.writeString(repo.resolve("new-folder/extra.py"), "print('kept')\n");
        assertEquals(repo, sessions.prepare(user, 7L));
        assertEquals("print('edited')\n", Files.readString(repo.resolve("hello.py")));
        assertTrue(Files.exists(repo.resolve("new-folder/extra.py")));
        assertThrows(ResponseStatusException.class, () -> sessions.save(repo, "hello.py", "old draft", (String)original.get("etag")));
        ReflectionTestUtils.setField(sessions, "bridge", Path.of("runtime/bridge.mjs").toAbsolutePath().toString());
        var folder = sessions.createEntry(repo, "", "manual", "dir");
        assertEquals("dir", folder.get("type"));
        sessions.createEntry(repo, "manual", "new.py", "file");
        assertThrows(ResponseStatusException.class, () -> sessions.createEntry(repo, "manual", "new.py", "file"));
        assertThrows(ResponseStatusException.class, () -> sessions.createEntry(repo, "", "../outside", "file"));
        var deleted = sessions.deleteEntry(repo, "manual");
        assertFalse(Files.exists(repo.resolve("manual")));
        sessions.restoreEntry(repo, UUID.fromString((String)deleted.get("trashId")));
        assertTrue(Files.exists(repo.resolve("manual/new.py")));
        String diff = sessions.changes(repo);
        assertTrue(diff.contains("new-folder/extra.py")); assertTrue(diff.contains("print('edited')"));
        assertThrows(ResponseStatusException.class, () -> sessions.file(repo, "../base"));
        assertThrows(ResponseStatusException.class, () -> sessions.file(repo, ".git/config"));
        Files.createSymbolicLink(repo.resolve("outside"), source);
        assertThrows(ResponseStatusException.class, () -> sessions.file(repo, "outside/hello.py"));
        verify(client, times(1)).getRepoUrl(7L);
        when(auth.getUserIdentity(user)).thenReturn(Map.of("username", "owner-name"));
        when(client.getProjectDetails(7L)).thenReturn(Map.of("userId", "owner-name"));
        assertEquals(repo, sessions.prepare(user, 7L));
        when(client.getProjectDetails(7L)).thenReturn(Map.of("userId", UUID.randomUUID().toString()));
        assertThrows(ResponseStatusException.class, () -> sessions.prepare(user, 7L));
    }
    private void git(Path cwd, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git")); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()); assertEquals(0, process.waitFor(), output);
    }
}
