package com.atlas.agent_service.service;

import com.atlas.agent_service.dto.JobRequest;
import com.atlas.agent_service.dto.JobResponse;
import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.NotificationFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.git.WorkspaceCacheService;
import com.atlas.agent_service.git.GitPatch;
import com.atlas.agent_service.llm.LlmProvider;
import com.atlas.agent_service.llm.LlmProviderFactory;
import com.atlas.agent_service.llm.LlmRequest;
import com.atlas.agent_service.llm.LlmResponse;
import com.atlas.agent_service.llm.LlmException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import com.atlas.agent_service.rag.RagService;
import com.atlas.agent_service.repository.AgentJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import com.atlas.shared.exception.InsufficientCreditsException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import feign.FeignException;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentJobService {

    private static final int CREDIT_COST = 1;

    private final AgentJobRepository jobRepository;
    private final UserAgentKeyService keyService;
    private final AuthFeignClient authFeignClient;
    private final NotificationFeignClient notificationFeignClient;
    private final com.atlas.agent_service.runtime.SessionWorkspaceService sessions;
    private final com.atlas.agent_service.runtime.AgentRuntimeService runtime;
    private final com.atlas.agent_service.runtime.AgentEventHub events;
    private final Executor agentTaskExecutor;

    public JobResponse submitJob(UUID userId, JobRequest request) {
        synchronized (sessions.lock(userId, request.projectId())) {
        sessions.checkOwner(userId, request.projectId());
        if (!List.of("openai", "claude").contains(request.provider()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interactive agents currently support OpenAI and Claude. Select one of these providers.");
        if (jobRepository.existsByProjectIdAndUserIdAndStatusIn(request.projectId(), userId, List.of(JobStatus.PENDING, JobStatus.RUNNING)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project already has an active agent. Stop it or wait for its reply.");
        int balance = authFeignClient.getCreditBalance(userId);
        if (balance < CREDIT_COST) {
            throw new InsufficientCreditsException(
                    "Insufficient credits. Current balance: " + balance);
        }

        String decryptedKey = keyService.decryptKey(userId, request.provider());

        AgentJob job = new AgentJob();
        job.setProjectId(request.projectId());
        job.setUserId(userId);
        job.setPrompt(request.prompt());
        job.setAgentProvider(request.provider());
        job.setStatus(JobStatus.PENDING);
        job.setEventsJson("[]");
        try { job = jobRepository.save(job); }
        catch (org.springframework.dao.DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "This project already has an active agent."); }

        JobResponse response = toResponse(job);
        UUID jobId = job.getId();
        try {
            agentTaskExecutor.execute(() -> executeJobAsync(jobId, userId, decryptedKey));
        } catch (RejectedExecutionException e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("The agent is busy. Please try again shortly.");
            job.setCompletedAt(Instant.now());
            return toResponse(jobRepository.save(job));
        }
        return response;
        }
    }

    public void executeJobAsync(UUID jobId, UUID userId, String apiKey) {
        AgentJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.RUNNING);
        jobRepository.save(job);
        notifyStatus(job, "RUNNING");

        Path workspace = null;
        try {
            if (events.cancelled(job)) throw new java.util.concurrent.CancellationException();
            workspace = sessions.prepare(userId, job.getProjectId());
            String responseText = runtime.run(job, workspace, apiKey);
            String diff = sessions.changes(workspace);

            authFeignClient.consumeCredits(userId, CREDIT_COST);

            job.setStatus(JobStatus.COMPLETED);
            job.setDiffOutput(diff.isBlank() ? null : diff);
            job.setResponseText(responseText);
            job.setCreditsConsumed(CREDIT_COST);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            notifyStatus(job, "COMPLETED");
            notifyCompletion(job);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            boolean stopped = events.cancelled(job) || e instanceof java.util.concurrent.CancellationException;
            job.setStatus(stopped ? JobStatus.CANCELLED : JobStatus.FAILED);
            job.setErrorMessage(stopped ? "Stopped. Your file changes were saved." : failureReason(e));
            if (workspace != null) try { job.setDiffOutput(sessions.changes(workspace)); } catch (Exception ignored) { /* The workspace itself remains durable. */ }
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            notifyStatus(job, job.getStatus().name());
            if (job.getStatus() == JobStatus.FAILED) notifyFailure(job);
        } finally { events.finish(job); }
    }

    static String failureReason(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (error instanceof LlmException) return message;
        if (error instanceof WebClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 401, 403 -> "The AI provider rejected the API key. Update it in Connect.";
                case 429 -> "The AI provider quota or rate limit was reached. Check the provider account.";
                case 404 -> "The selected AI model is unavailable.";
                default -> "The AI provider couldn't process the request. Try again.";
            };
        }
        if (error instanceof WebClientRequestException) return "Couldn't connect to the AI provider.";
        if (message.contains("WorkspaceFeignClient")) return "Couldn't load the project repository.";
        if (message.contains("getGithubToken")) return "Couldn't access the GitHub connection. Reconnect GitHub in Connect.";
        if (error instanceof InsufficientCreditsException || message.toLowerCase().contains("insufficient credits")) {
            return "Not enough credits. Add credits to continue.";
        }
        if (error instanceof FeignException) return "Couldn't reach a required service. Try again.";
        if (message.startsWith("Diff validation failed")) return "The generated changes could not be applied.";
        if (message.contains("dimensions") && (message.contains("embedding") || message.contains("repo_embeddings"))) {
            return "Code indexing storage is misconfigured. The embedding dimensions do not match.";
        }
        return "The agent couldn't complete the request. Try again.";
    }

    public AgentJob ownedJob(UUID jobId, UUID userId) {
        AgentJob job = jobRepository.findById(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!userId.equals(job.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return job;
    }

    public void stop(UUID jobId, UUID userId) {
        AgentJob job = ownedJob(jobId, userId);
        if (List.of(JobStatus.PENDING, JobStatus.RUNNING).contains(job.getStatus())) events.cancel(job);
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterruptedTurns() {
        for (var job : jobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING))) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("The agent service restarted. Your files were saved. Send another prompt to resume.");
            job.setCompletedAt(Instant.now()); jobRepository.save(job);
        }
    }

    public JobResponse getJob(UUID jobId, UUID userId) {
        AgentJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (!job.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return toResponse(job);
    }

    public List<JobResponse> getJobsByProject(Long projectId, UUID userId) {
        return jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(j -> j.getUserId().equals(userId))
                .map(this::toResponse)
                .toList();
    }

    private void notifyStatus(AgentJob job, String step) {
        job.setStep(step);
        jobRepository.save(job);
        try {
            notificationFeignClient.sendWebSocketUpdate(Map.of(
                    "jobId", job.getId().toString(),
                    "step", step,
                    "status", job.getStatus().name(),
                    "message", job.getErrorMessage() != null ? job.getErrorMessage() : step,
                    "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to send status update: {}", e.getMessage());
        }
    }

    private void notifyCompletion(AgentJob job) {
        try {
            notificationFeignClient.sendPush(Map.of(
                    "userId", job.getUserId().toString(),
                    "jobId", job.getId().toString(),
                    "status", "COMPLETED",
                    "message", "Your agent response is ready"
            ));
        } catch (Exception e) {
            log.warn("Failed to send completion notification: {}", e.getMessage());
        }
    }

    private void notifyFailure(AgentJob job) {
        try {
            notificationFeignClient.sendPush(Map.of(
                    "userId", job.getUserId().toString(),
                    "jobId", job.getId().toString(),
                    "status", "FAILED",
                    "message", job.getErrorMessage() != null ? job.getErrorMessage() : "The agent couldn't complete the request."
            ));
        } catch (Exception e) {
            log.warn("Failed to send failure notification: {}", e.getMessage());
        }
    }

    private JobResponse toResponse(AgentJob job) {
        return new JobResponse(
                job.getId(),
                job.getProjectId(),
                job.getStatus(),
                job.getDiffOutput(),
                job.getErrorMessage(),
                job.getCreditsConsumed(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getPrompt(),
                job.getResponseText(),
                job.getAgentProvider(),
                job.getStep(),
                job.getEventsJson() == null ? List.of() : tools.jackson.databind.json.JsonMapper.builder().build().readValue(job.getEventsJson(), List.class),
                job.getEventsJson() != null
        );
    }
}
