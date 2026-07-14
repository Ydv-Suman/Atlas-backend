package com.atlas.workspace_service.exception;

public class WorkspaceAlreadyExistsException extends RuntimeException {
    public WorkspaceAlreadyExistsException(String message) {
        super(message);
    }
}
