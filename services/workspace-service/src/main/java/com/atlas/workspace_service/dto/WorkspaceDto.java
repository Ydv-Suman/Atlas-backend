package com.atlas.workspace_service.dto;

import com.atlas.workspace_service.entity.RepoOwnership;
import com.atlas.workspace_service.entity.RepoVisibility;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class WorkspaceDto  {

    private  String projectName;
    private String framework;
    private String githubUrl;
    private String repoOwner;
    private RepoOwnership repoOwnership;
    private RepoVisibility repoVisibility;
    private String projectType;
    private Instant createdAt;
    private Instant lastSyncedAt;


}
