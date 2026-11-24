package com.atlas.agent_service.git;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.repository.AgentJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobPreviewServiceTest {
    @TempDir Path repository;

    private void initialize() throws Exception {
        git("init");
        Files.writeString(repository.resolve("old.txt"), "old\n");
        Files.writeString(repository.resolve("remove.txt"), "remove\n");
        git("add", ".");
        git("-c", "user.name=Atlas Test", "-c", "user.email=atlas-test@example.invalid", "commit", "-m", "fixture");
    }

    @Test
    void generatedNewModifiedAndDeletedFilesHaveCorrectPreviewsWithoutChangingTheCheckout() throws Exception {
        initialize();
        Files.createDirectories(repository.resolve("nested"));
        Files.writeString(repository.resolve("nested/hello world.py"), "print(\"Hello, world!\")\n");
        Files.writeString(repository.resolve("old.txt"), "updated\n");
        Files.delete(repository.resolve("remove.txt"));
        git("add", "--intent-to-add", "--all");
        String diff = git("diff", "HEAD");
        git("reset", "--hard", "HEAD");
        Files.deleteIfExists(repository.resolve("nested/hello world.py"));
        Files.deleteIfExists(repository.resolve("nested"));
        var files = JobPreviewService.preview(repository, diff);
        assertEquals(3, files.size());
        assertEquals("print(\"Hello, world!\")\n", files.stream().filter(f -> f.path().endsWith(".py")).findFirst().orElseThrow().content());
        assertEquals("updated\n", files.stream().filter(f -> f.path().equals("old.txt")).findFirst().orElseThrow().content());
        assertTrue(files.stream().filter(f -> f.path().equals("remove.txt")).findFirst().orElseThrow().deleted());
        assertFalse(Files.exists(repository.resolve("nested")));
        assertEquals("old\n", Files.readString(repository.resolve("old.txt")));
        assertTrue(Files.exists(repository.resolve("remove.txt")));
        assertEquals("", git("status", "--porcelain"));
    }

    @Test
    void previewsCannotReadSymlinkTargets() throws Exception {
        initialize();
        String diff = """
                diff --git a/link b/link
                new file mode 120000
                --- /dev/null
                +++ b/link
                @@ -0,0 +1 @@
                +/etc/passwd
                """;
        assertThrows(java.io.IOException.class, () -> JobPreviewService.preview(repository, diff));
        assertFalse(Files.exists(repository.resolve("link")));
    }

    @Test
    void onlyTheJobOwnerCanReadGeneratedFiles() {
        var jobs = mock(AgentJobRepository.class);
        var cache = mock(WorkspaceCacheService.class);
        var client = mock(WorkspaceFeignClient.class);
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setStatus(JobStatus.COMPLETED);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        assertThrows(ResponseStatusException.class, () -> new JobPreviewService(jobs, cache, client, mock(com.atlas.agent_service.runtime.SessionWorkspaceService.class)).getFiles(job.getId(), UUID.randomUUID()));
        verifyNoInteractions(cache, client);
    }

    private String git(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
