package com.atlas.agent_service.controller;

import com.atlas.agent_service.git.SessionGitService;
import com.atlas.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/agent/sessions/project/{projectId}/git")
@RequiredArgsConstructor
public class SessionGitController {
    private final SessionGitService git;
    @GetMapping
    public ApiResponse<Object> status(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId) throws Exception {
        return ApiResponse.success("200", "Git status", git.execute(user, projectId, "status", Map.of()));
    }
    @GetMapping("/diff")
    public ApiResponse<Object> diff(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId, @RequestParam String path) throws Exception {
        return ApiResponse.success("200", "File diff", git.execute(user, projectId, "diff", Map.of("filePath", path)));
    }
    @GetMapping("/history")
    public ApiResponse<Object> history(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId) throws Exception {
        return ApiResponse.success("200", "Commit history", git.execute(user, projectId, "history", Map.of()));
    }
    @PostMapping("/{action}")
    public ApiResponse<Object> action(@RequestHeader("X-User-Id") UUID user, @PathVariable Long projectId,
                                     @PathVariable String action, @RequestBody Map<String,Object> request) throws Exception {
        return ApiResponse.success("200", "Git action", git.execute(user, projectId, action, request));
    }
}
