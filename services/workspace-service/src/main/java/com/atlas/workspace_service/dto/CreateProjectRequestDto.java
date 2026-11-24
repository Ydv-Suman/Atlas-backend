package com.atlas.workspace_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequestDto {

    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 255, message = "Project name must be between 1 and 255 characters")
    private String projectName;

    private String framework;

    @NotBlank(message = "GitHub URL is required")
    @Size(min = 1, max = 500, message = "GitHub URL must be between 1 and 500 characters")
    private String githubUrl;

    @NotBlank(message = "Repo owner is required")
    private String repoOwner;

    @NotBlank(message = "Repo ownership is required")
    @Pattern(regexp = "(?i)(OWNER|COLLABORATOR|FORK)", message = "Repo ownership must be OWNER, COLLABORATOR, or FORK")
    private String repoOwnership;

    @NotBlank(message = "Repo visibility is required")
    @Pattern(regexp = "(?i)(PUBLIC|PRIVATE)", message = "Repo visibility must be PUBLIC or PRIVATE")
    private String repoVisibility;

    private String projectType;

    private Boolean createIfNotExists = false;
}
