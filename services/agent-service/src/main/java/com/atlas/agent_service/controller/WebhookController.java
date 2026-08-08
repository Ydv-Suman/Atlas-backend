package com.atlas.agent_service.controller;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.feign.NotificationFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/agent/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final AgentJobRepository agentJobRepository;
    private final NotificationFeignClient notificationFeignClient;
    private final ObjectMapper objectMapper;

    @Value("${atlas.github.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/github")
    public ResponseEntity<String> handleGithubWebhook(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody String payload) {

        if (!verifySignature(payload, signature)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"workflow_run".equals(event) && !"check_suite".equals(event)) {
            return ResponseEntity.ok("Ignored event: " + event);
        }

        processWorkflowEvent(payload);
        return ResponseEntity.ok("OK");
    }

    private void processWorkflowEvent(String payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(payload, Map.class);

            String action = (String) body.get("action");
            if (!"completed".equals(action)) return;

            Map<String, Object> workflowRun = (Map<String, Object>) body.get("workflow_run");
            if (workflowRun == null) return;

            String headBranch = (String) workflowRun.get("head_branch");
            String conclusion = (String) workflowRun.get("conclusion");
            String htmlUrl = (String) workflowRun.get("html_url");

            if (headBranch == null || !headBranch.startsWith("atlas/")) return;

            String jobIdStr = headBranch.replace("atlas/", "").split("-")[0];
            UUID jobId;
            try {
                jobId = UUID.fromString(jobIdStr);
            } catch (IllegalArgumentException e) {
                log.debug("Branch {} not an Atlas job branch", headBranch);
                return;
            }

            agentJobRepository.findById(jobId).ifPresent(job -> {
                log.info("Workflow completed for job {}: {}", jobId, conclusion);

                notifyWorkflowResult(job, conclusion, htmlUrl);
            });

        } catch (Exception e) {
            log.error("Failed to process workflow event: {}", e.getMessage(), e);
        }
    }

    private void notifyWorkflowResult(AgentJob job, String conclusion, String url) {
        try {
            notificationFeignClient.sendWebSocketUpdate(Map.of(
                    "type", "WORKFLOW_RESULT",
                    "userId", job.getUserId().toString(),
                    "jobId", job.getId().toString(),
                    "projectId", job.getProjectId().toString(),
                    "conclusion", conclusion,
                    "url", url != null ? url : ""
            ));
        } catch (Exception e) {
            log.warn("Failed to send workflow notification: {}", e.getMessage());
        }
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }
}
