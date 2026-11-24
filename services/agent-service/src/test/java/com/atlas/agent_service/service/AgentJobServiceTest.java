package com.atlas.agent_service.service;

import com.atlas.agent_service.dto.JobRequest;
import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.AuthFeignClient;
import com.atlas.agent_service.feign.NotificationFeignClient;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.git.WorkspaceCacheService;
import com.atlas.agent_service.llm.LlmProviderFactory;
import com.atlas.agent_service.rag.RagService;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.shared.exception.InsufficientCreditsException;
import com.atlas.agent_service.controller.AgentExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentJobServiceTest {
    private final AgentJobRepository repository = mock(AgentJobRepository.class);
    private final UserAgentKeyService keys = mock(UserAgentKeyService.class);
    private final AuthFeignClient auth = mock(AuthFeignClient.class);
    private final WorkspaceCacheService cache = mock(WorkspaceCacheService.class);
    private final Executor executor = mock(Executor.class);
    private final WorkspaceFeignClient workspace = mock(WorkspaceFeignClient.class);
    private final NotificationFeignClient notifications = mock(NotificationFeignClient.class);
    private final com.atlas.agent_service.runtime.SessionWorkspaceService sessions = mock(com.atlas.agent_service.runtime.SessionWorkspaceService.class);
    private final com.atlas.agent_service.runtime.AgentRuntimeService runtime = mock(com.atlas.agent_service.runtime.AgentRuntimeService.class);
    private final com.atlas.agent_service.runtime.AgentEventHub events = mock(com.atlas.agent_service.runtime.AgentEventHub.class);
    private final AgentJobService service = new AgentJobService(repository, keys, auth, notifications, sessions, runtime, events, executor);
    private final UUID userId = UUID.randomUUID();
    private final JobRequest request = new JobRequest(1L, "Fix the README", "openai");

    @org.junit.jupiter.api.BeforeEach
    void setupLock() { when(sessions.lock(any(), any())).thenReturn(new Object()); }

    private void allowSubmission() {
        when(auth.getCreditBalance(userId)).thenReturn(1);
        when(repository.save(any(AgentJob.class))).thenAnswer(invocation -> {
            AgentJob job = invocation.getArgument(0);
            if (job.getId() == null) job.setId(UUID.randomUUID());
            return job;
        });
    }

    @Test
    void returnsPendingWithoutRunningTheAgentOnTheRequestThread() {
        allowSubmission();
        var response = service.submitJob(userId, request);
        assertEquals(JobStatus.PENDING, response.status());
        verify(executor).execute(any(Runnable.class));
        verifyNoInteractions(cache);
        verify(repository, never()).findById(any());
    }

    @Test
    void noCreditsPreventsJobCreationAndCloning() {
        when(auth.getCreditBalance(userId)).thenReturn(0);
        var error = assertThrows(InsufficientCreditsException.class, () -> service.submitJob(userId, request));
        assertTrue(error.getMessage().contains("Insufficient credits"));
        verify(repository, never()).save(any());
        verifyNoInteractions(executor, cache, keys);
    }

    @Test
    void creditErrorsKeepTheirMessageInTheHttpResponse() {
        var request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/agent/jobs");
        var response = new AgentExceptionHandler().insufficientCredits(
                new InsufficientCreditsException("Insufficient credits. Current balance: 0"), request);
        assertEquals(402, response.getStatusCode().value());
        assertEquals("Insufficient credits. Current balance: 0", response.getBody().getErrorMessage());
    }

    @Test
    void failuresExposeAReasonInsteadOfInternalRequestDetails() {
        assertEquals("Code indexing storage is misconfigured. The embedding dimensions do not match.", AgentJobService.failureReason(
                new RuntimeException("ERROR: expected 384 dimensions, not 768 [insert into repo_embeddings]")));
        assertEquals("Couldn't connect to the code indexing service.", AgentJobService.failureReason(
                new com.atlas.agent_service.llm.LlmException("Couldn't connect to the code indexing service.")));
        assertEquals("The AI provider quota or rate limit was reached. Check the provider account.", AgentJobService.failureReason(
                org.springframework.web.reactive.function.client.WebClientResponseException.create(429, "Too Many Requests", null, null, null)));
        assertEquals("Couldn't load the project repository.", AgentJobService.failureReason(
                new RuntimeException("[500] during [GET] http://workspaceservice:8090 [WorkspaceFeignClient#getRepoUrl]")));
        assertEquals("Not enough credits. Add credits to continue.", AgentJobService.failureReason(
                new InsufficientCreditsException("Insufficient credits. Current balance: 0")));
        assertEquals("The agent couldn't complete the request. Try again.", AgentJobService.failureReason(
                new RuntimeException("secret internal stack details")));
        assertEquals("Couldn't access the GitHub connection. Reconnect GitHub in Connect.", AgentJobService.failureReason(
                new RuntimeException("[AuthFeignClient#getGithubToken(UUID)] request failed")));
    }

    @Test
    void failureNotificationsUseTheReceivingServicesPayloadContract() throws Exception {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setUserId(userId);
        job.setProjectId(1L);
        when(repository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        when(sessions.prepare(userId, 1L)).thenThrow(new RuntimeException("WorkspaceFeignClient failed"));
        service.executeJobAsync(job.getId(), userId, "test-key");
        verify(notifications).sendPush(argThat(payload ->
                payload.get("userId").equals(userId.toString())
                && payload.get("jobId").equals(job.getId().toString())
                && payload.get("status").equals("FAILED")
                && payload.get("message").equals("Couldn't load the project repository.")
                && !payload.containsKey("tokens")));
        verify(notifications).sendWebSocketUpdate(argThat(payload ->
                payload.get("status").equals("FAILED") && payload.get("step").equals("FAILED")));
        verify(auth, never()).getDeviceTokens(any());
        assertEquals("/api/notify", NotificationFeignClient.class
                .getAnnotation(org.springframework.cloud.openfeign.FeignClient.class).path());
    }

    @Test
    void aFullQueueReturnsAFailedJobWithAnActionableError() {
        allowSubmission();
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        var response = service.submitJob(userId, request);
        assertEquals(JobStatus.FAILED, response.status());
        assertEquals("The agent is busy. Please try again shortly.", response.errorMessage());
        assertNotNull(response.completedAt());
    }
    @Test
    void usesThePersistentRuntimeAndSavesItsReplyAndCumulativeChanges() throws Exception {
        var job = new AgentJob(); job.setId(UUID.randomUUID()); job.setUserId(userId); job.setProjectId(1L);
        job.setAgentProvider("openai"); job.setPrompt("Continue editing the file");
        var repo = java.nio.file.Path.of("/tmp/session/repo");
        when(repository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        when(sessions.prepare(userId, 1L)).thenReturn(repo);
        when(runtime.run(job, repo, "test-key")).thenReturn("Updated the same file.");
        when(sessions.changes(repo)).thenReturn("cumulative diff");
        service.executeJobAsync(job.getId(), userId, "test-key");
        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("Updated the same file.", job.getResponseText());
        assertEquals("cumulative diff", job.getDiffOutput());
        verify(auth).consumeCredits(userId, 1);
        verify(events).finish(job);
    }

    @Test
    void anotherActiveTurnPreventsConcurrentEdits() {
        when(repository.existsByProjectIdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(true);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.submitJob(userId, request));
        verifyNoInteractions(executor, keys);
    }
    @Test
    void staleEditorSavesReturnAConflictWithTheirReason() {
        var request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/agent/sessions/project/7/file");
        var response = new AgentExceptionHandler().requestError(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "The file changed. Reopen it before saving your edits."), request);
        assertEquals(409, response.getStatusCode().value());
        assertEquals("The file changed. Reopen it before saving your edits.", response.getBody().getErrorMessage());
    }

}
