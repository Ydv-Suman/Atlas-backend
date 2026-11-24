package com.atlas.agent_service.controller;

import com.atlas.agent_service.runtime.SessionWorkspaceService;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/agent/sessions/project/{projectId}")
@RequiredArgsConstructor
public class AgentSessionController {
    private final SessionWorkspaceService sessions;
    private final AgentJobRepository jobs;
    @GetMapping("/tree")
    public ApiResponse<List<Map<String,Object>>> tree(@RequestHeader("X-User-Id") UUID user,
            @PathVariable Long projectId, @RequestParam(defaultValue = "") String path) throws Exception {
        return ApiResponse.success("200", "Session files", sessions.tree(sessions.prepare(user, projectId), path));
    }
    @GetMapping("/file")
    public ApiResponse<Map<String,Object>> file(@RequestHeader("X-User-Id") UUID user,
            @PathVariable Long projectId, @RequestParam String path) throws Exception {
        return ApiResponse.success("200", "Session file", sessions.file(sessions.prepare(user, projectId), path));
    }
    public record FileEdit(String content, String etag) {}
    @PutMapping("/file")
    public ApiResponse<Map<String,Object>> save(@RequestHeader("X-User-Id") UUID user,
            @PathVariable Long projectId, @RequestParam String path, @RequestBody FileEdit edit) throws Exception {
        synchronized (sessions.lock(user, projectId)) {
        var repo = sessions.prepare(user, projectId);
        if (jobs.existsByProjectIdAndUserIdAndStatusIn(projectId, user, List.of(JobStatus.PENDING, JobStatus.RUNNING)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop the agent before saving editor changes.");
        return ApiResponse.success("200", "Saved in workspace", sessions.save(repo, path, edit.content(), edit.etag()));
        }
    }
    public record EntryCreate(String parent, String name, String kind) {}
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String,Object>> create(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId,
                                                  @RequestBody EntryCreate entry) throws Exception {
        synchronized (sessions.lock(user, projectId)) {
            var repo = sessions.prepare(user, projectId); checkIdle(user, projectId);
            return ApiResponse.success("201", "Created in workspace", sessions.createEntry(repo, entry.parent(), entry.name(), entry.kind()));
        }
    }
    @DeleteMapping("/entries")
    public ApiResponse<Map<String,Object>> delete(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId,
                                                  @RequestParam String path) throws Exception {
        synchronized (sessions.lock(user, projectId)) {
            var repo = sessions.prepare(user, projectId); checkIdle(user, projectId);
            return ApiResponse.success("200", "Moved to workspace trash", sessions.deleteEntry(repo, path));
        }
    }
    @PostMapping("/trash/{trashId}/restore")
    public ApiResponse<Map<String,Object>> restore(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId,
                                                   @PathVariable UUID trashId) throws Exception {
        synchronized (sessions.lock(user, projectId)) {
            var repo = sessions.prepare(user, projectId); checkIdle(user, projectId);
            return ApiResponse.success("200", "Restored", sessions.restoreEntry(repo, trashId));
        }
    }
    private void checkIdle(UUID user, Long projectId) {
        if (jobs.existsByProjectIdAndUserIdAndStatusIn(projectId, user, List.of(JobStatus.PENDING, JobStatus.RUNNING)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop the agent before changing files or folders.");
    }

}
