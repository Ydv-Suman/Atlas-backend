package com.atlas.agent_service.git;

import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.agent_service.runtime.SessionWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionGitServiceTest {
    @TempDir Path temporary;
    @Test void checksProjectOwnershipBeforeReadingStatusOrCredentials() throws Exception {
        var sessions = mock(SessionWorkspaceService.class); var jobs = mock(AgentJobRepository.class);
        var auth = mock(AuthFeignClient.class); var workspace = mock(WorkspaceFeignClient.class);
        UUID user = UUID.randomUUID(); when(sessions.lock(user, 7L)).thenReturn(new Object());
        when(sessions.prepare(user, 7L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Another user's project"));
        var service = new SessionGitService(sessions, jobs, auth, workspace);
        var error = assertThrows(ResponseStatusException.class, () -> service.execute(user, 7L, "status", Map.of()));
        assertEquals(403, error.getStatusCode().value()); verifyNoInteractions(auth, workspace, jobs);
    }
    @Test void blocksMutationsDuringAnAgentTurnBeforeFetchingCredentials() throws Exception {
        var sessions = mock(SessionWorkspaceService.class); var jobs = mock(AgentJobRepository.class);
        var auth = mock(AuthFeignClient.class); var workspace = mock(WorkspaceFeignClient.class);
        UUID user = UUID.randomUUID(); when(sessions.lock(user, 7L)).thenReturn(new Object()); when(sessions.prepare(user, 7L)).thenReturn(temporary);
        when(jobs.existsByProjectIdAndUserIdAndStatusIn(eq(7L), eq(user), any())).thenReturn(true);
        var service = new SessionGitService(sessions, jobs, auth, workspace);
        for (String action : List.of("commit", "switch", "push", "pull", "fetch", "rebase", "merge", "create-branch", "continue", "abort")) {
            var error = assertThrows(ResponseStatusException.class, () -> service.execute(user, 7L, action, Map.of())); assertEquals(409, error.getStatusCode().value());
        }
        verifyNoInteractions(auth, workspace);
    }
    @Test void readsRealGitAndCommitsOnlyReviewedContentWithoutAcceptingClientProcessSettings() throws Exception {
        UUID user = UUID.randomUUID(); Path session = temporary.resolve(user.toString()).resolve("7"), repo = session.resolve("repo"); Files.createDirectories(repo);
        git(repo, "init", "--initial-branch=main"); git(repo, "config", "user.name", "Test"); git(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("hello.py"), "base"); git(repo, "add", "."); git(repo, "commit", "-m", "base");
        Path base = temporary.resolve(".metadata").resolve(user.toString()).resolve("7/base"); Files.createDirectories(base.getParent()); Files.writeString(base, git(repo, "rev-parse", "HEAD"));
        var sessions = mock(SessionWorkspaceService.class); var jobs = mock(AgentJobRepository.class); var auth = mock(AuthFeignClient.class); var workspace = mock(WorkspaceFeignClient.class);
        when(sessions.lock(user, 7L)).thenReturn(new Object()); when(sessions.prepare(user, 7L)).thenReturn(repo); when(sessions.directory(user, 7L)).thenReturn(session);
        when(sessions.isolatedCommand(eq(7L), any())).thenAnswer(call -> call.getArgument(1));
        when(auth.getUserIdentity(user)).thenReturn(Map.of("name", "Test", "email", "test@example.invalid"));
        var service = new SessionGitService(sessions, jobs, auth, workspace); ReflectionTestUtils.setField(service, "bridge", Path.of("runtime/bridge.mjs").toAbsolutePath().toString());
        Files.writeString(repo.resolve("hello.py"), "edited");
        @SuppressWarnings("unchecked") var status = (Map<String,Object>) service.execute(user, 7L, "status", Map.of("root", "/invalid", "allowLocal", true, "token", "ignored"));
        assertEquals("main", status.get("branch"));
        service.execute(user, 7L, "commit", Map.of("message", "edited", "expectedRevision", status.get("revision")));
        assertEquals("edited", git(repo, "log", "-1", "--format=%s")); assertEquals(git(repo, "rev-parse", "HEAD"), Files.readString(base));
        verify(auth, never()).getGithubToken(any()); verifyNoInteractions(workspace);
    }
    private String git(Path cwd, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("git")); command.addAll(List.of(arguments)); var process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()); assertEquals(0, process.waitFor(), output); return output.trim();
    }
}
