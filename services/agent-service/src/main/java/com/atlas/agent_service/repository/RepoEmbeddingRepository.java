package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.RepoEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepoEmbeddingRepository extends JpaRepository<RepoEmbedding, UUID> {

    List<RepoEmbedding> findByProjectId(UUID projectId);

    List<RepoEmbedding> findByProjectIdAndFilePath(UUID projectId, String filePath);

    void deleteByProjectId(UUID projectId);

    void deleteByProjectIdAndFilePath(UUID projectId, String filePath);
}
