package com.atlas.workspace_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Repo ownership is required")
    private String repoOwnership;

    @NotNull(message = "Repo visibility is required")
    private String repoVisibility;

    private String projectType;

    private Boolean createIfNotExists = false;
}
