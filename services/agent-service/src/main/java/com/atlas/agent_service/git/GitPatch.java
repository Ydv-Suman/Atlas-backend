package com.atlas.agent_service.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitPatch {
    private GitPatch() {}

    public static void apply(Path directory, Path patch, boolean checkOnly) throws IOException, InterruptedException {
        String output = "";
        for (boolean inaccurateEof : new boolean[] { false, true }) {
            var command = new ArrayList<>(List.of("git", "-c", "safe.directory=" + directory.toAbsolutePath(), "apply", "--recount"));
            if (checkOnly) command.add("--check");
            if (inaccurateEof) command.add("--inaccurate-eof");
            command.add(patch.toAbsolutePath().toString());
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() == 0) return;
        }
        throw new IOException("Diff validation failed: " + output);
    }
}
