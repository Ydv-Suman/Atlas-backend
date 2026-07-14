package com.atlas.workspace_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubReposDto {

    private long id;

    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private String description;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("clone_url")
    private String cloneUrl;

    private String language;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("private")
    private boolean isPrivate;

    private boolean fork;

    @JsonProperty("stargazers_count")
    private int stargazersCount;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("owner")
    private Owner owner;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Owner {
        private String login;

        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
