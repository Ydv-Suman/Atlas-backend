package com.atlas.workspace_service.service;

import com.atlas.workspace_service.dto.CreateProjectRequestDto;
import com.atlas.workspace_service.dto.FileTreeEntryDto;
import com.atlas.workspace_service.dto.GithubReposDto;
import com.atlas.workspace_service.dto.WorkspaceDto;

import java.util.List;

public interface IWorkspaceService {

    List<GithubReposDto> getRepoList(String username);

    WorkspaceDto createProject(CreateProjectRequestDto createProjectRequestDto, String userId);

    List<WorkspaceDto> listProjects(String userId);

    WorkspaceDto getProject(Long id, String userId);

    void deleteProject(Long id, String userId);

    List<FileTreeEntryDto> getFileTree(Long projectId, String path, String username);
}
