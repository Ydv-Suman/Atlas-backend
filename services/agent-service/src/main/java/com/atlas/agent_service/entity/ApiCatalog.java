package com.atlas.agent_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "api_catalog")
public class ApiCatalog {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoints", nullable = false, columnDefinition = "jsonb")
    private String endpoints;

    @NotNull
    @Column(name = "commit_hash", nullable = false, length = 40)
    private String commitHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
