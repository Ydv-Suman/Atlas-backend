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
@Table(name = "repo_embeddings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "file_path", "chunk_index"}))
public class RepoEmbedding {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @NotNull
    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @NotNull
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @NotNull
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;

    @Column(name = "commit_hash", length = 40)
    private String commitHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
