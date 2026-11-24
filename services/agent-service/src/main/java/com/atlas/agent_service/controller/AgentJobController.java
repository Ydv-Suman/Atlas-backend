package com.atlas.agent_service.controller;

import com.atlas.agent_service.dto.JobRequest;
import com.atlas.agent_service.dto.JobResponse;
import com.atlas.agent_service.service.AgentJobService;
import com.atlas.agent_service.git.JobPreviewService;
import com.atlas.agent_service.dto.JobFilePreview;
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
    private final JobPreviewService jobPreviewService;
    private final com.atlas.agent_service.runtime.AgentEventHub events;

    @GetMapping(value = "/{jobId}/events", produces = "text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID jobId,
            @RequestParam(defaultValue = "0") int after) {
        return events.stream(agentJobService.ownedJob(jobId, userId), after);
    }

    @PostMapping("/{jobId}/stop")
    public ResponseEntity<Void> stop(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID jobId) {
        agentJobService.stop(jobId, userId); return ResponseEntity.accepted().build();
    }

    public record AgentAnswer(@jakarta.validation.constraints.NotBlank String requestId, boolean approved,
                              java.util.Map<String,Object> answers) {}
    @PostMapping("/{jobId}/answer")
    public ResponseEntity<Void> answer(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID jobId,
                                       @Valid @RequestBody AgentAnswer answer) {
        events.answer(agentJobService.ownedJob(jobId, userId), answer.requestId(), answer.approved(), answer.answers());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{jobId}/files")
    public ResponseEntity<ApiResponse<List<JobFilePreview>>> getGeneratedFiles(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID jobId) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("200", "Generated files", jobPreviewService.getFiles(jobId, userId)));
    }

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
