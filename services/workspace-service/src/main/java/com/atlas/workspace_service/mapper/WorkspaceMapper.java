package com.atlas.workspace_service.mapper;

import com.atlas.workspace_service.dto.CreateProjectRequestDto;
import com.atlas.workspace_service.dto.WorkspaceDto;
import com.atlas.workspace_service.entity.RepoOwnership;
import com.atlas.workspace_service.entity.RepoVisibility;
import com.atlas.workspace_service.entity.WorkspaceEntity;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceMapper {

    public WorkspaceEntity toEntity(CreateProjectRequestDto dto, String userId) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setUserId(userId);
        entity.setProjectName(dto.getProjectName());
        entity.setFramework(dto.getFramework());
        entity.setGithubUrl(dto.getGithubUrl());
        entity.setRepoOwner(dto.getRepoOwner());
        entity.setRepoOwnership(RepoOwnership.valueOf(dto.getRepoOwnership().toUpperCase()));
        entity.setRepoVisibility(RepoVisibility.valueOf(dto.getRepoVisibility().toUpperCase()));
        entity.setProjectType(dto.getProjectType());
        return entity;
    }

    public WorkspaceDto toDto(WorkspaceEntity entity) {
        return WorkspaceDto.builder()
                .id(entity.getId())
                .projectName(entity.getProjectName())
                .framework(entity.getFramework())
                .githubUrl(entity.getGithubUrl())
                .repoOwner(entity.getRepoOwner())
                .repoOwnership(entity.getRepoOwnership())
                .repoVisibility(entity.getRepoVisibility())
                .projectType(entity.getProjectType())
                .createdAt(entity.getCreatedAt())
                .lastSyncedAt(entity.getLastSynchedAt())
                .build();
    }
}
