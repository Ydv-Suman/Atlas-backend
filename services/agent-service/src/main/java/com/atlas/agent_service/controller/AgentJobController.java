package com.atlas.agent_service.controller;

import com.atlas.agent_service.dto.JobRequest;
import com.atlas.agent_service.dto.JobResponse;
import com.atlas.agent_service.service.AgentJobService;
import com.atlas.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent/jobs")
@RequiredArgsConstructor
public class AgentJobController {

    private final AgentJobService agentJobService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> submitJob(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody JobRequest request) {

        JobResponse response = agentJobService.submitJob(userId, request);
        return ResponseEntity.accepted().body(
                ApiResponse.success("202", "Job submitted", response));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID jobId) {

        JobResponse response = agentJobService.getJob(jobId, userId);
        return ResponseEntity.ok(ApiResponse.success("200", "Job details", response));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<JobResponse>>> getJobsByProject(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long projectId) {

        List<JobResponse> responses = agentJobService.getJobsByProject(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("200", "Project jobs", responses));
    }
}
