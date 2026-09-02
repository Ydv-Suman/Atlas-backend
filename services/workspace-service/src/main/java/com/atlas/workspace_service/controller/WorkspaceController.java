package com.atlas.workspace_service.controller;

import com.atlas.workspace_service.constants.WorkspaceConstants;
import com.atlas.workspace_service.dto.CreateProjectRequestDto;
import com.atlas.workspace_service.dto.FileTreeEntryDto;
import com.atlas.workspace_service.dto.GithubReposDto;
import com.atlas.workspace_service.dto.WorkspaceDto;
import com.atlas.workspace_service.service.IWorkspaceService;
import com.atlas.shared.dto.ApiResponse;
import com.atlas.shared.security.JwtClaims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final IWorkspaceService workspaceService;

    @GetMapping(value = "/repos", version = "1.0")
    public ResponseEntity<ApiResponse<List<GithubReposDto>>> getRepoList(Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        List<GithubReposDto> repos = workspaceService.getRepoList(claims.username());
        return ResponseEntity.ok(ApiResponse.success(WorkspaceConstants.STATUS_200, WorkspaceConstants.MESSAGE_PROJECTS_FETCHED, repos));
    }

    @PostMapping(value = "/projects", version = "1.0")
    public ResponseEntity<ApiResponse<WorkspaceDto>> createProject(
            @Valid @RequestBody CreateProjectRequestDto requestDto,
            Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        WorkspaceDto created = workspaceService.createProject(requestDto, claims.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(WorkspaceConstants.STATUS_201, WorkspaceConstants.MESSAGE_PROJECT_CREATED, created));
    }

    @GetMapping(value = "/projects", version = "1.0")
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> listProjects(Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        List<WorkspaceDto> projects = workspaceService.listProjects(claims.username());
        return ResponseEntity.ok(ApiResponse.success(WorkspaceConstants.STATUS_200, WorkspaceConstants.MESSAGE_PROJECTS_FETCHED, projects));
    }

    @GetMapping(value = "/projects/{id}", version = "1.0")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getProject(
            @PathVariable Long id,
            Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        WorkspaceDto project = workspaceService.getProject(id, claims.username());
        return ResponseEntity.ok(ApiResponse.success(WorkspaceConstants.STATUS_200, WorkspaceConstants.MESSAGE_PROJECTS_FETCHED, project));
    }

    @DeleteMapping(value = "/projects/{id}", version = "1.0")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable Long id,
            Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        workspaceService.deleteProject(id, claims.username());
        return ResponseEntity.ok(ApiResponse.success(WorkspaceConstants.STATUS_200, WorkspaceConstants.MESSAGE_PROJECT_DELETED));
    }

    @GetMapping(value = "/projects/{id}/tree", version = "1.0")
    public ResponseEntity<ApiResponse<List<FileTreeEntryDto>>> getFileTree(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "/") String path,
            Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        List<FileTreeEntryDto> tree = workspaceService.getFileTree(id, path, claims.username());
        return ResponseEntity.ok(ApiResponse.success(WorkspaceConstants.STATUS_200, WorkspaceConstants.MESSAGE_PROJECTS_FETCHED, tree));
    }
}
