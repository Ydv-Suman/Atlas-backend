package com.atlas.workspace_service.controller;

import com.atlas.workspace_service.entity.WorkspaceEntity;
import com.atlas.workspace_service.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/workspace/internal")
@RequiredArgsConstructor
public class InternalWorkspaceController {

    private final WorkspaceRepository workspaceRepository;

    @GetMapping("/project/{projectId}")
    public Map<String, Object> getProjectDetails(@PathVariable Long projectId) {
        WorkspaceEntity project = workspaceRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return Map.of(
                "id", project.getId(),
                "userId", project.getUserId(),
                "githubUrl", project.getGithubUrl(),
                "projectName", project.getProjectName(),
                "repoOwner", project.getRepoOwner()
        );
    }

    @GetMapping("/project/{projectId}/repo-url")
    public String getRepoUrl(@PathVariable Long projectId) {
        WorkspaceEntity project = workspaceRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return project.getGithubUrl();
    }
}
