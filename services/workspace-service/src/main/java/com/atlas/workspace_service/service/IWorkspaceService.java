package com.atlas.workspace_service.service;

import com.atlas.workspace_service.dto.GithubReposDto;

import java.util.List;

public interface IWorkspaceService {

    List<GithubReposDto> getRepoList(String username);
}
