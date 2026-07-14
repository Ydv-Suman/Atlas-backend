package com.atlas.workspace_service.constants;

public final class WorkspaceConstants {

    private WorkspaceConstants() {
    }

    public static final String STATUS_200 = "200";
    public static final String STATUS_201 = "201";
    public static final String STATUS_204 = "204";

    public static final String MESSAGE_PROJECT_CREATED = "Project created successfully";
    public static final String MESSAGE_PROJECT_DELETED = "Project deleted successfully";
    public static final String MESSAGE_PROJECTS_FETCHED = "Projects fetched successfully";

    public static final String MESSAGE_DUPLICATE_WORKSPACE = "Workspace already exists for this repository";
    public static final String MESSAGE_WORKSPACE_NOT_FOUND = "Workspace not found";
    public static final String MESSAGE_REPO_NOT_FOUND = "No repository found with name '%s/%s'. Set createIfNotExists to true to create it on GitHub automatically.";
    public static final String MESSAGE_REPO_NO_ACCESS = "You don't have access to this repository";
    public static final String MESSAGE_REPO_NO_PUSH = "You don't have push access to this repository";
    public static final String MESSAGE_REPO_CREATE_FAILED = "Failed to create repository on GitHub";
    public static final String MESSAGE_REPO_VERIFY_FAILED = "Unable to verify repository access";
    public static final String MESSAGE_INVALID_GITHUB_URL = "Invalid GitHub URL";
}
