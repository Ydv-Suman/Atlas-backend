package com.atlas.agent_service.service;

import com.atlas.agent_service.dto.JobRequest;
import com.atlas.agent_service.dto.JobResponse;
import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.entity.UserAgentKey;
import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.NotificationFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.git.WorkspaceCacheService;
import com.atlas.agent_service.llm.LlmProvider;
import com.atlas.agent_service.llm.LlmProviderFactory;
import com.atlas.agent_service.llm.LlmRequest;
import com.atlas.agent_service.llm.LlmResponse;
import com.atlas.agent_service.rag.RagService;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.agent_service.repository.UserAgentKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentJobService {

    private static final int CREDIT_COST = 1;

    private final AgentJobRepository jobRepository;
    private final UserAgentKeyRepository keyRepository;
    private final AuthFeignClient authFeignClient;
    private final WorkspaceFeignClient workspaceFeignClient;
    private final NotificationFeignClient notificationFeignClient;
    private final WorkspaceCacheService workspaceCacheService;
    private final LlmProviderFactory providerFactory;
    private final RagService ragService;

    public JobResponse submitJob(UUID userId, JobRequest request) {
        int balance = authFeignClient.getCreditBalance(userId);
        if (balance < CREDIT_COST) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient credits. Current balance: " + balance);
        }

        UserAgentKey agentKey = keyRepository.findByUserIdAndProvider(userId, request.provider())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No API key configured for provider: " + request.provider()));

        AgentJob job = new AgentJob();
        job.setProjectId(request.projectId());
        job.setUserId(userId);
        job.setPrompt(request.prompt());
        job.setAgentProvider(request.provider());
        job.setStatus(JobStatus.PENDING);
        job = jobRepository.save(job);

        executeJobAsync(job.getId(), userId, agentKey.getEncryptedKey());

        return toResponse(job);
    }

    @Async("agentTaskExecutor")
    public void executeJobAsync(UUID jobId, UUID userId, String apiKey) {
        AgentJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.RUNNING);
        jobRepository.save(job);
        notifyStatus(job, "RUNNING");

        try {
            String repoUrl = workspaceFeignClient.getRepoUrl(job.getProjectId());
            String workspacePath = workspaceCacheService.getWorkspace(
                    job.getProjectId(), repoUrl, userId);

            notifyStatus(job, "INDEXING");
            ragService.indexRepository(job.getProjectId(), workspacePath, null);

            notifyStatus(job, "RETRIEVING_CONTEXT");
            List<String> contextChunks = ragService.retrieve(job.getProjectId(), job.getPrompt());
            String fileTree = buildFileTree(workspacePath);

            notifyStatus(job, "GENERATING_DIFF");
            LlmProvider provider = providerFactory.getProvider(job.getAgentProvider());
            LlmRequest llmRequest = new LlmRequest(job.getPrompt(), fileTree, contextChunks);
            LlmResponse llmResponse = provider.generateDiff(llmRequest, apiKey);

            notifyStatus(job, "VALIDATING");
            validateDiff(workspacePath, llmResponse.diff());

            authFeignClient.consumeCredits(userId, CREDIT_COST);

            job.setStatus(JobStatus.COMPLETED);
            job.setDiffOutput(llmResponse.diff());
            job.setCreditsConsumed(CREDIT_COST);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            notifyStatus(job, "COMPLETED");
            notifyCompletion(job);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            notifyStatus(job, "FAILED");
            notifyFailure(job);
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

    public List<JobResponse> getJobsByProject(UUID projectId, UUID userId) {
        return jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(j -> j.getUserId().equals(userId))
                .map(this::toResponse)
                .toList();
    }

    private void validateDiff(String workspacePath, String diff) throws IOException, InterruptedException {
        Path diffFile = Files.createTempFile("atlas-validate", ".patch");
        Files.writeString(diffFile, diff);

        try {
            Process process = new ProcessBuilder("git", "apply", "--check", diffFile.toAbsolutePath().toString())
                    .directory(Path.of(workspacePath).toFile())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("Diff validation failed: " + output);
            }
        } finally {
            Files.deleteIfExists(diffFile);
        }
    }

    private String buildFileTree(String workspacePath) {
        Path root = Path.of(workspacePath);
        var sb = new StringBuilder();

        try (Stream<Path> walk = Files.walk(root, 4)) {
            walk.filter(p -> !p.toString().contains("/.git/"))
                    .filter(p -> !p.toString().contains("/node_modules/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(p -> {
                        int depth = root.relativize(p).getNameCount();
                        String indent = "  ".repeat(Math.max(0, depth - 1));
                        sb.append(indent).append(p.getFileName()).append("\n");
                    });
        } catch (IOException e) {
            log.warn("Failed to build file tree: {}", e.getMessage());
        }

        return sb.toString();
    }

    private void notifyStatus(AgentJob job, String step) {
        try {
            notificationFeignClient.sendWebSocketUpdate(Map.of(
                    "type", "JOB_STATUS",
                    "userId", job.getUserId().toString(),
                    "jobId", job.getId().toString(),
                    "projectId", job.getProjectId().toString(),
                    "step", step
            ));
        } catch (Exception e) {
            log.warn("Failed to send status update: {}", e.getMessage());
        }
    }

    private void notifyCompletion(AgentJob job) {
        try {
            List<String> tokens = authFeignClient.getDeviceTokens(job.getUserId());
            notificationFeignClient.sendPush(Map.of(
                    "tokens", tokens,
                    "title", "Diff Ready",
                    "body", "Your code changes are ready for review",
                    "data", Map.of("jobId", job.getId().toString())
            ));
        } catch (Exception e) {
            log.warn("Failed to send completion notification: {}", e.getMessage());
        }
    }

    private void notifyFailure(AgentJob job) {
        try {
            List<String> tokens = authFeignClient.getDeviceTokens(job.getUserId());
            notificationFeignClient.sendPush(Map.of(
                    "tokens", tokens,
                    "title", "Job Failed",
                    "body", job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error",
                    "data", Map.of("jobId", job.getId().toString())
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
                job.getCompletedAt()
        );
    }
}
