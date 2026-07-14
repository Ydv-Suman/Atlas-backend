package com.atlas.workspace_service.exception;

public class GitHubRepoException extends RuntimeException {
    public GitHubRepoException(String message) {
        super(message);
    }

    public GitHubRepoException(String message, Throwable cause) {
        super(message, cause);
    }
}
