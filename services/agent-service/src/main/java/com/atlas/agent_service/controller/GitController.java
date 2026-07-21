package com.atlas.agent_service.controller;

import com.atlas.agent_service.dto.CreatePrRequest;
import com.atlas.agent_service.dto.PushRequest;
import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.git.GitService;
import com.atlas.agent_service.git.WorkspaceCacheService;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/agent/git")
@RequiredArgsConstructor
public class GitController {

    private final GitService gitService;
    private final WorkspaceCacheService workspaceCacheService;
    private final AgentJobRepository agentJobRepository;
    private final WorkspaceFeignClient workspaceFeignClient;

    @PostMapping(value = "/push/{jobId}", version = "1.0")
    public ResponseEntity<ApiResponse<Map<String, String>>> pushDiff(
            @PathVariable UUID jobId,
            @Valid @RequestBody PushRequest request) throws Exception {

        AgentJob job = agentJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.success("400", "Job not in COMPLETED state"));
        }

        if (job.getDiffOutput() == null || job.getDiffOutput().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.success("400", "Job has no diff output"));
        }

        String repoUrl = workspaceFeignClient.getRepoUrl(job.getProjectId());
        String clonePath = workspaceCacheService.getWorkspace(job.getProjectId(), repoUrl, job.getUserId());

        String sha = gitService.pushDiff(
                clonePath,
                job.getDiffOutput(),
                request.branchName(),
                request.commitMessage(),
                job.getUserId());

        log.info("Push complete: jobId={}, branch={}, sha={}", jobId, request.branchName(), sha);
        return ResponseEntity.ok(ApiResponse.success("200", "Push successful",
                Map.of("sha", sha, "branch", request.branchName())));
    }

    @PostMapping(value = "/pr/{jobId}", version = "1.0")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPullRequest(
            @PathVariable UUID jobId,
            @Valid @RequestBody CreatePrRequest request) {

        AgentJob job = agentJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        Map<String, Object> project = workspaceFeignClient.getProjectDetails(job.getProjectId());
        String repoFullName = (String) project.get("repoFullName");
        String prUrl = gitService.createPullRequest(
                job.getUserId(),
                repoFullName,
                request.branchName(),
                request.title(),
                request.description());

        log.info("PR created: jobId={}, url={}", jobId, prUrl);
        return ResponseEntity.ok(ApiResponse.success("200", "Pull request created",
                Map.of("prUrl", prUrl)));
    }
}
