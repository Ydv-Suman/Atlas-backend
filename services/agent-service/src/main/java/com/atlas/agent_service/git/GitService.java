package com.atlas.agent_service.git;

import com.atlas.agent_service.feign.AuthFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    private final AuthFeignClient authFeignClient;
    private final WebClient.Builder webClientBuilder;

    public Git cloneRepo(UUID userId, String repoUrl, String clonePath) throws Exception {
        CredentialsProvider creds = credentialsFor(userId);
        log.info("Cloning repo: {} to {}", repoUrl, clonePath);

        return Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(clonePath))
                .setCredentialsProvider(creds)
                .setDepth(1)
                .call();
    }

    public void syncWorkspace(UUID userId, String clonePath) throws Exception {
        CredentialsProvider creds = credentialsFor(userId);

        try (Git git = Git.open(new File(clonePath))) {
            git.fetch().setCredentialsProvider(creds).call();
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/main").call();
            log.info("Synced workspace {} to origin/main", clonePath);
        }
    }

    public String pushDiff(String clonePath, String diff, String branchName, String commitMessage, UUID userId) throws Exception {
        CredentialsProvider creds = credentialsFor(userId);
        Map<String, String> identity = authFeignClient.getUserIdentity(userId);

        try (Git git = Git.open(new File(clonePath))) {
            git.checkout().setCreateBranch(true).setName(branchName).call();

            Path diffFile = Files.createTempFile("atlas-temp", ".patch");
            Files.writeString(diffFile, diff);

            Process process = new ProcessBuilder("git", "apply", diffFile.toAbsolutePath().toString())
                    .directory(new File(clonePath))
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            Files.deleteIfExists(diffFile);

            if (exitCode != 0) {
                throw new IOException("git apply failed (exit " + exitCode + "): " + output);
            }

            git.add().addFilepattern(".").call();

            RevCommit commit = git.commit().setMessage(commitMessage)
                    .setAuthor(identity.get("name"), identity.get("email")).call();

            git.push().setCredentialsProvider(creds).setRemote("origin").call();

            String sha = commit.getId().getName();
            log.info("Pushed branch {} with commit {}", branchName, sha);
            return sha;
        }
    }

    public String createPullRequest(UUID userId, String repoFullName, String branchName,
                                    String title, String description) {
        String token = authFeignClient.getGithubToken(userId);

        Map<String, String> body = Map.of(
                "head", branchName,
                "base", "main",
                "title", title,
                "body", description
        );

        Map<?, ?> response = webClientBuilder.build()
                .post()
                .uri("https://api.github.com/repos/{repo}/pulls", repoFullName)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String prUrl = response != null ? (String) response.get("html_url") : null;
        log.info("Created PR: {}", prUrl);
        return prUrl;
    }

    public void injectWorkflow(String clonePath, UUID userId) throws Exception {
        Path workflowDir = Path.of(clonePath, ".github", "workflows");
        Path targetFile = workflowDir.resolve("atlas-verify.yml");

        if (Files.exists(targetFile)) {
            log.info("Workflow already exists at {}", targetFile);
            return;
        }

        var templateStream = getClass().getClassLoader().getResourceAsStream("workflows/atlas-verify.yml");

        if (templateStream == null) {
            log.warn("No workflow template found at workflows/atlas-verify.yml");
            return;
        }

        Files.createDirectories(workflowDir);
        Files.copy(templateStream, targetFile);

        CredentialsProvider creds = credentialsFor(userId);
        Map<String, String> identity = authFeignClient.getUserIdentity(userId);
        try (Git git = Git.open(new File(clonePath))) {
            git.add().addFilepattern(".github/workflows/atlas-verify.yml").call();

            git.commit()
                    .setMessage("ci: add Atlas verification workflow")
                    .setAuthor(identity.get("name"), identity.get("email"))
                    .call();

            git.push()
                    .setCredentialsProvider(creds)
                    .setRemote("origin")
                    .call();

            log.info("Injected and pushed atlas-verify workflow to {}", clonePath);
        }
    }

    private CredentialsProvider credentialsFor(UUID userId) {
        String token = authFeignClient.getGithubToken(userId);
        return new UsernamePasswordCredentialsProvider(token, "");
    }
}
