package com.atlas.workspace_service.service.impl;

import com.atlas.workspace_service.constants.WorkspaceConstants;
import com.atlas.workspace_service.dto.CreateProjectRequestDto;
import com.atlas.workspace_service.dto.FileTreeEntryDto;
import com.atlas.workspace_service.dto.GithubReposDto;
import com.atlas.workspace_service.dto.WorkspaceDto;
import com.atlas.workspace_service.entity.WorkspaceEntity;
import com.atlas.workspace_service.exception.GitHubRepoException;
import com.atlas.workspace_service.exception.WorkspaceAlreadyExistsException;
import com.atlas.workspace_service.exception.WorkspaceNotFoundException;
import com.atlas.workspace_service.feign.AuthFeignClient;
import com.atlas.workspace_service.mapper.WorkspaceMapper;
import com.atlas.workspace_service.repository.WorkspaceRepository;
import com.atlas.workspace_service.service.IWorkspaceService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements IWorkspaceService {

    private static final String GITHUB_API_URL = "https://api.github.com";

    private final AuthFeignClient authFeignClient;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public List<GithubReposDto> getRepoList(String username) {
        String githubToken = fetchGithubToken(username);

        return restClient.get()
                .uri(GITHUB_API_URL + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    @Transactional
    public WorkspaceDto createProject(CreateProjectRequestDto dto, String userId) {
        if (workspaceRepository.existsByUserIdAndGithubUrl(userId, dto.getGithubUrl())) {
            throw new WorkspaceAlreadyExistsException(WorkspaceConstants.MESSAGE_DUPLICATE_WORKSPACE);
        }

        String githubToken = fetchGithubToken(userId);
        String repoName = extractRepoName(dto.getGithubUrl());

        if (!repoExists(dto.getRepoOwner(), repoName, githubToken)) {
            if (Boolean.TRUE.equals(dto.getCreateIfNotExists())) {
                createGithubRepo(repoName, dto.getRepoVisibility(), githubToken);
                dto.setGithubUrl("https://github.com/" + dto.getRepoOwner() + "/" + repoName);
                dto.setRepoOwnership("OWNER");
            } else {
                throw new GitHubRepoException(
                        String.format(WorkspaceConstants.MESSAGE_REPO_NOT_FOUND, dto.getRepoOwner(), repoName));
            }
        } else {
            verifyPushAccess(dto.getRepoOwner(), repoName, githubToken);
        }

        WorkspaceEntity entity = workspaceMapper.toEntity(dto, userId);
        WorkspaceEntity saved = workspaceRepository.save(entity);
        return workspaceMapper.toDto(saved);
    }

    private String fetchGithubToken(String username) {
        try {
            return authFeignClient.getGithubToken(username);
        } catch (FeignException.NotFound e) {
            throw new GitHubRepoException("GitHub is not connected. Please connect your GitHub account first.");
        }
    }

    private boolean repoExists(String owner, String repo, String token) {
        try {
            restClient.get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            throw new GitHubRepoException(WorkspaceConstants.MESSAGE_REPO_NO_ACCESS);
        }
    }

    @SuppressWarnings("unchecked")
    private void verifyPushAccess(String owner, String repo, String token) {
        Map<String, Object> repoData = restClient.get()
                .uri(GITHUB_API_URL + "/repos/{owner}/{repo}", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (repoData == null) {
            throw new GitHubRepoException(WorkspaceConstants.MESSAGE_REPO_VERIFY_FAILED);
        }

        Map<String, Boolean> permissions = (Map<String, Boolean>) repoData.get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("push"))) {
            throw new GitHubRepoException(WorkspaceConstants.MESSAGE_REPO_NO_PUSH);
        }
    }

    private void createGithubRepo(String repoName, String visibility, String token) {
        boolean isPrivate = "PRIVATE".equalsIgnoreCase(visibility);

        Map<String, Object> body = Map.of(
                "name", repoName,
                "private", isPrivate,
                "auto_init", true
        );

        try {
            restClient.post()
                    .uri(GITHUB_API_URL + "/user/repos")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new GitHubRepoException(WorkspaceConstants.MESSAGE_REPO_CREATE_FAILED, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileTreeEntryDto> getFileTree(Long projectId, String path, String username) {
        WorkspaceEntity project = workspaceRepository.findByIdAndUserId(projectId, username)
                .orElseThrow(() -> new WorkspaceNotFoundException(WorkspaceConstants.MESSAGE_WORKSPACE_NOT_FOUND));

        String githubToken = fetchGithubToken(username);
        String owner = project.getRepoOwner();
        String repo = extractRepoName(project.getGithubUrl());
        String contentsPath = (path == null || path.isBlank() || "/".equals(path)) ? "" : path;

        List<Map<String, Object>> contents = restClient.get()
                .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/contents/{path}", owner, repo, contentsPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (contents == null) {
            return List.of();
        }

        return contents.stream()
                .map(entry -> FileTreeEntryDto.builder()
                        .name((String) entry.get("name"))
                        .path((String) entry.get("path"))
                        .type("dir".equals(entry.get("type")) ? "dir" : "file")
                        .size(entry.get("size") != null ? ((Number) entry.get("size")).longValue() : null)
                        .build())
                .sorted((a, b) -> {
                    int typeCompare = a.getType().compareTo(b.getType());
                    return typeCompare != 0 ? typeCompare : a.getName().compareToIgnoreCase(b.getName());
                })
                .toList();
    }

    private String extractRepoName(String githubUrl) {
        String path = githubUrl.replaceFirst("https://github.com/", "");
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new GitHubRepoException(WorkspaceConstants.MESSAGE_INVALID_GITHUB_URL);
        }
        return parts[1].replace(".git", "");
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceDto> listProjects(String userId) {
        return workspaceRepository.findAllByUserId(userId)
                .stream()
                .map(workspaceMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceDto getProject(Long id, String userId) {
        WorkspaceEntity entity = workspaceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(WorkspaceConstants.MESSAGE_WORKSPACE_NOT_FOUND));
        return workspaceMapper.toDto(entity);
    }

    @Override
    @Transactional
    public void deleteProject(Long id, String userId) {
        WorkspaceEntity entity = workspaceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new WorkspaceNotFoundException(WorkspaceConstants.MESSAGE_WORKSPACE_NOT_FOUND));
        workspaceRepository.delete(entity);
    }
}
