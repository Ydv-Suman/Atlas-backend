package com.atlas.workspace_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;

@Entity
@Getter
@Setter
@ToString
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name="user_id", nullable = false)
    private String userId;

    @Size(min = 1, max = 255)
    @Column(name="project_name",nullable = false)
    private String projectName;

    @Size(max=255)
    @Column(name = "framework")
    private String framework;

    @Size(max = 500)
    @Column(name = "github_url", nullable = false)
    private String githubUrl;

    @Size(max = 150)
    @Column(name= "repo_owner", nullable = false)
    private String repoOwner;

    @Enumerated(EnumType.STRING)
    private RepoOwnership repoOwnership;


    @Enumerated(EnumType.STRING)
    private RepoVisibility repoVisibility;

    @Size(max = 255)
    @Column(name= "project_type")
    private String projectType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_synched_at", nullable = false)
    private Instant lastSynchedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        lastSynchedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        lastSynchedAt = Instant.now();
    }
}
