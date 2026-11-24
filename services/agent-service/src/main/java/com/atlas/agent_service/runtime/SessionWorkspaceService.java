package com.atlas.agent_service.runtime;

import com.atlas.agent_service.feign.WorkspaceFeignClient;
import com.atlas.agent_service.git.GitService;
import com.atlas.agent_service.git.GitPatch;
import com.atlas.agent_service.repository.AgentJobRepository;
import com.atlas.agent_service.entity.JobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SessionWorkspaceService {
    private final GitService git;
    private final com.atlas.agent_service.feign.AuthFeignClient auth;
    private final WorkspaceFeignClient workspace;
    private final AgentJobRepository jobs;
    @Value("${atlas.runtime.workspace-root:/tmp/atlas/sessions}") private String workspaceRoot;
    @Value("${atlas.runtime.isolate-users:false}") private boolean isolateUsers;
    @Value("${atlas.runtime.bridge:/app/runtime/bridge.mjs}") private String bridge;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public Object lock(UUID user, Long project) { return locks.computeIfAbsent(directory(user, project).toString(), key -> new Object()); }
    public Path directory(UUID user, Long project) { return Path.of(workspaceRoot, user.toString(), project.toString()); }
    public void checkOwner(UUID user, Long project) {
        var details = workspace.getProjectDetails(project);
        String owner = String.valueOf(details.get("userId"));
        if (!user.toString().equals(owner) && !owner.equals(auth.getUserIdentity(user).get("username")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This project belongs to another user.");
    }
    public Path prepare(UUID user, Long project) throws Exception {
        checkOwner(user, project);
        Path root = directory(user, project);
        synchronized (lock(user, project)) {
            Path repo = root.resolve("repo");
            if (Files.isSymbolicLink(repo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The workspace must not be a symbolic link.");
            Path metadata = Path.of(workspaceRoot, ".metadata", user.toString(), project.toString());
            Files.createDirectories(metadata);
            Files.setPosixFilePermissions(Path.of(workspaceRoot, ".metadata"), PosixFilePermissions.fromString("rwx------"));
            if (!Files.exists(repo.resolve(".git"))) {
                Files.createDirectories(root);
                Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
                if (isolateUsers) { Files.setAttribute(root, "unix:uid", (int)uid(project)); Files.setAttribute(root, "unix:gid", (int)uid(project)); }
                // A failed first clone is retried in a new directory; existing work is never reset.
                Path clone = Files.createTempDirectory(root, "clone-");
                try {
                    git.cloneRepo(user, workspace.getRepoUrl(project), clone.toString()).close();
                    if (Files.exists(repo)) throw new IllegalStateException("The session repository is incomplete. Recovery is required.");
                    Files.move(clone, repo, StandardCopyOption.ATOMIC_MOVE);
                } finally { if (Files.exists(clone) && isEmpty(clone)) Files.delete(clone); }
            }
            if (!Files.exists(metadata.resolve("base"))) {
                try (var repository = org.eclipse.jgit.api.Git.open(repo.toFile())) {
                    Files.writeString(metadata.resolve("base"), repository.getRepository().resolve("HEAD").name());
                }
            }
            if (!Files.exists(metadata.resolve("legacy-imported"))) {
                var legacy = jobs.findByProjectIdOrderByCreatedAtDesc(project).reversed();
                for (var job : legacy) if (user.equals(job.getUserId()) && job.getStatus() == JobStatus.COMPLETED
                        && job.getEventsJson() == null && job.getDiffOutput() != null && !job.getDiffOutput().isBlank()) {
                    Path patch = Files.createTempFile("atlas-import-", ".patch");
                    try { Files.writeString(patch, job.getDiffOutput()); GitPatch.apply(repo, patch, false); }
                    catch (Exception ignored) { /* Conflicting historical previews never overwrite existing work. */ }
                    finally { Files.deleteIfExists(patch); }
                }
                Files.writeString(metadata.resolve("legacy-imported"), "complete");
            }
            Files.createDirectories(root.resolve("home/tmp"));
            Files.createDirectories(root.resolve("state"));
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
            if (isolateUsers && !Objects.equals(Files.getAttribute(repo, "unix:uid"), (int)uid(project))) {
                Process process = new ProcessBuilder("chown", "-hR", uid(project) + ":" + uid(project), root.toString()).start();
                if (process.waitFor() != 0) throw new IllegalStateException("Could not isolate the project workspace.");
            }
            return repo;
        }
    }
    private boolean isEmpty(Path path) throws Exception { try (var files = Files.list(path)) { return files.findAny().isEmpty(); } }
    public long uid(Long project) {
        if (project == null || project < 1 || project > 2_000_000_000L) throw new IllegalArgumentException("Invalid project ID");
        return 100000L + project;
    }
    public List<String> isolatedCommand(Long project, List<String> command) {
        if (!isolateUsers) return command;
        var args = new ArrayList<>(List.of("setpriv", "--reuid=" + uid(project), "--regid=" + uid(project), "--clear-groups", "--bounding-set=-all", "--no-new-privs"));
        args.addAll(command); return args;
    }
    public String gitCommand(Path repo, String... args) throws Exception {
        Long project = Long.valueOf(repo.getParent().getFileName().toString());
        var command = new ArrayList<>(List.of("git", "-c", "safe.directory=" + repo, "-c", "core.fsmonitor=false", "-c", "core.hooksPath=/dev/null"));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(isolatedCommand(project, command)).directory(repo.toFile()).redirectErrorStream(true);
        builder.environment().clear(); builder.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");
        var process = builder.start();
        var reader = CompletableFuture.supplyAsync(() -> { try { return process.getInputStream().readNBytes(8_000_001); } catch (Exception e) { throw new CompletionException(e); } });
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Workspace operation timed out."); }
        byte[] output = reader.get(5, TimeUnit.SECONDS);
        if (output.length > 8_000_000) throw new IllegalStateException("The project change is too large to display.");
        if (process.exitValue() != 0) throw new IllegalStateException("Could not read the project workspace.");
        return new String(output, StandardCharsets.UTF_8);
    }
    public String changes(Path repo) throws Exception {
        gitCommand(repo, "add", "--intent-to-add", "--all");
        String base = base(repo);
        return gitCommand(repo, "diff", "--no-ext-diff", "--no-textconv", "--binary", base, "--", ".");
    }
    public String base(Path repo) throws Exception {
        return Files.readString(Path.of(workspaceRoot, ".metadata", repo.getParent().getParent().getFileName().toString(), repo.getParent().getFileName().toString(), "base")).trim();
    }
    @SuppressWarnings("unchecked")
    private Object fileOperation(Path repo, String operation, String path, String content, String etag) throws Exception {
        Long project = Long.valueOf(repo.getParent().getFileName().toString());
        var command = isolatedCommand(project, List.of("node", Path.of(bridge).getParent().resolve("files.mjs").toString()));
        var builder = new ProcessBuilder(command).directory(repo.toFile());
        builder.environment().clear(); builder.environment().put("PATH", isolateUsers ? "/usr/local/bin:/usr/bin:/bin" : System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin"));
        Process process = builder.start();
        var request = new HashMap<String,Object>();
        request.put("root", repo.toString()); request.put("operation", operation); request.put("filePath", path);
        request.put("content", content); request.put("etag", etag);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        try (var input = process.getOutputStream()) { input.write(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)); }
        var output = CompletableFuture.supplyAsync(() -> { try { return process.getInputStream().readNBytes(2_000_001); } catch (Exception error) { throw new CompletionException(error); } });
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("File operation timed out."); }
        byte[] bytes = output.get(5, TimeUnit.SECONDS);
        if (process.exitValue() != 0 || bytes.length > 2_000_000) throw new IllegalStateException("Could not access this workspace file.");
        var response = json.readTree(bytes);
        if (!response.path("ok").asBoolean()) throw new ResponseStatusException(HttpStatus.valueOf(response.path("status").asInt()), response.path("message").asString());
        return json.convertValue(response.get("data"), Object.class);
    }
    public Path safeFile(Path repo, String path) throws Exception {
        Path relative = Path.of(path == null ? "" : path);
        if (relative.isAbsolute()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a project-relative path.");
        Path file = repo.resolve(relative).normalize();
        if (!file.startsWith(repo) || relative.toString().contains("\0")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path.");
        Path current = repo;
        for (Path part : repo.relativize(file)) {
            if (part.toString().equals(".git")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Git internals are not editable.");
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbolic links are not editable.");
        }
        return file;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> file(Path repo, String path) throws Exception {
        if (isolateUsers) return (Map<String,Object>)fileOperation(repo, "read", path, null, null);
        Path file = safeFile(repo, path);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        if (Files.size(file) > 1_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This file is too large to edit.");
        byte[] bytes = Files.readAllBytes(file);
        String content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if (content.indexOf('\0') >= 0) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file is not text.");
        return Map.of("path", path, "content", content, "etag", hash(bytes));
    }
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> tree(Path repo, String path) throws Exception {
        if (isolateUsers) return (List<Map<String,Object>>)fileOperation(repo, "tree", path, null, null);
        Path directory = safeFile(repo, path);
        if (!Files.isDirectory(directory)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found.");
        try (var files = Files.list(directory)) {
            return files.filter(file -> !file.getFileName().toString().equals(".git") && !Files.isSymbolicLink(file))
                .sorted(Comparator.comparing((Path file) -> !Files.isDirectory(file)).thenComparing(file -> file.getFileName().toString().toLowerCase()))
                .limit(2000).map(file -> { try { return Map.<String,Object>of("name", file.getFileName().toString(), "path", repo.relativize(file).toString(), "type", Files.isDirectory(file) ? "dir" : "file", "size", Files.isRegularFile(file) ? Files.size(file) : 0L); }
                    catch (Exception e) { throw new IllegalStateException("Could not list this folder.", e); } }).toList();
        }
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> save(Path repo, String path, String content, String expectedHash) throws Exception {
        if (isolateUsers) return (Map<String,Object>)fileOperation(repo, "save", path, content, expectedHash);
        Path file = safeFile(repo, path);
        if (content == null || content.length() > 1_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file content.");
        String current = Files.exists(file) ? hash(Files.readAllBytes(file)) : "";
        if (!Objects.equals(expectedHash, current)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The file changed. Reopen it before saving your edits.");
        Files.createDirectories(file.getParent());
        if (isolateUsers) {
            int owner = (int)uid(Long.valueOf(repo.getParent().getFileName().toString()));
            for (Path parent = file.getParent(); parent.startsWith(repo) && !parent.equals(repo); parent = parent.getParent()) {
                Files.setAttribute(parent, "unix:uid", owner); Files.setAttribute(parent, "unix:gid", owner);
            }
        }
        Path temporary = Files.createTempFile(file.getParent(), ".atlas-save-", ".tmp");
        try { Files.writeString(temporary, content);
            if (Files.exists(file)) Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file));
            if (isolateUsers) { Files.setAttribute(temporary, "unix:uid", (int)uid(Long.valueOf(repo.getParent().getFileName().toString()))); Files.setAttribute(temporary, "unix:gid", (int)uid(Long.valueOf(repo.getParent().getFileName().toString()))); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
        return file(repo, path);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> createEntry(Path repo, String parent, String name, String kind) throws Exception {
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.chars().anyMatch(character -> character < 32 || character == 127))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a file or folder name without slashes.");
        if (!"file".equals(kind) && !"dir".equals(kind)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose file or folder.");
        String path = (parent == null || parent.isEmpty()) ? name : parent + "/" + name;
        safeFile(repo, path);
        return (Map<String,Object>)fileOperation(repo, "create-" + kind, path, null, null);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> deleteEntry(Path repo, String path) throws Exception {
        return (Map<String,Object>)fileOperation(repo, "delete", path, null, null);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> restoreEntry(Path repo, UUID trashId) throws Exception {
        return (Map<String,Object>)fileOperation(repo, "restore", trashId.toString(), null, null);
    }
    private String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
