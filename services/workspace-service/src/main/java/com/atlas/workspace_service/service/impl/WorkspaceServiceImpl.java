package com.atlas.workspace_service.service.impl;

import com.atlas.workspace_service.dto.GithubReposDto;
import com.atlas.workspace_service.feign.AuthFeignClient;
import com.atlas.workspace_service.service.IWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements IWorkspaceService {

    private static final String GITHUB_API_URL = "https://api.github.com";

    private final AuthFeignClient authFeignClient;

    @Override
    public List<GithubReposDto> getRepoList(String username) {
        String githubToken = authFeignClient.getGithubToken(username);

        RestClient restClient = RestClient.create();

        return restClient.get()
                .uri(GITHUB_API_URL + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
