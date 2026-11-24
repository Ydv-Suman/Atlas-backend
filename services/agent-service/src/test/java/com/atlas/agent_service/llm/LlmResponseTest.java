package com.atlas.agent_service.llm;

import org.junit.jupiter.api.Test;
import com.atlas.agent_service.git.GitPatch;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LlmResponseTest {
    @TempDir Path directory;

    @Test
    void normalizesFencesAndLineEndingsWithoutRemovingContextSpaces() {
        var response = new LlmResponse("```diff\r\n--- a/file\r\n+++ b/file\r\n@@ -1 +1,2 @@\r\n one\r\n+two\r\n```", 0, "openai");
        assertEquals("--- a/file\n+++ b/file\n@@ -1 +1,2 @@\n one\n+two\n", response.diff());
        assertTrue(new LlmResponse("--- a/file\n+++ b/file\n ", 0, "openai").diff().endsWith(" \n"));
    }

    @Test
    void gitCanValidateAndApplyTheReturnedPatchWithRecount() throws Exception {
        assertEquals(0, new ProcessBuilder("git", "init", directory.toString()).redirectErrorStream(true).start().waitFor());
        Files.writeString(directory.resolve("file"), "one\n");
        var response = new LlmResponse("--- a/file\n+++ b/file\n@@ -1,4 +1,8 @@\n one\n+two", 0, "openai");
        Path patch = directory.resolve("change.patch");
        Files.writeString(patch, response.diff());
        GitPatch.apply(directory, patch, true);
        assertEquals("one\n", Files.readString(directory.resolve("file")));
        GitPatch.apply(directory, patch, false);
        assertEquals("one\ntwo\n", Files.readString(directory.resolve("file")));
    }

    @Test
    void filesWithoutAnEndingNewlineCanBePatched() throws Exception {
        Files.writeString(directory.resolve("file"), "one");
        Path patch = directory.resolve("change.patch");
        Files.writeString(patch, new LlmResponse("--- a/file\n+++ b/file\n@@ -1 +1,2 @@\n one\n+two", 0, "openai").diff());
        GitPatch.apply(directory, patch, true);
        GitPatch.apply(directory, patch, false);
        assertEquals("one\ntwo", Files.readString(directory.resolve("file")));
    }
}
