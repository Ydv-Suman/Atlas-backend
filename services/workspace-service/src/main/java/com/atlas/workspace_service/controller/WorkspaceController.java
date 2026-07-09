package com.atlas.workspace_service.controller;

import com.atlas.workspace_service.dto.GithubReposDto;
import com.atlas.workspace_service.service.IWorkspaceService;
import com.atlas.shared.security.JwtClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final IWorkspaceService workspaceService;

    @GetMapping(value = "/repos", version = "1.0")
    public ResponseEntity<List<GithubReposDto>> getRepoList(Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        return ResponseEntity.ok(workspaceService.getRepoList(claims.username()));
    }
}
