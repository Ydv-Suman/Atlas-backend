package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.RepoEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepoEmbeddingRepository extends JpaRepository<RepoEmbedding, UUID> {

    @Query(value = """
            SELECT * FROM repo_embeddings
            WHERE project_id = :projectId
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<RepoEmbedding> findSimilar(
            @Param("projectId") Long projectId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit);

    List<RepoEmbedding> findByProjectId(Long projectId);

    @Modifying
    @Query("DELETE FROM RepoEmbedding r WHERE r.projectId = :projectId AND r.filePath = :filePath")
    void deleteByProjectIdAndFilePath(@Param("projectId") Long projectId,
                                      @Param("filePath") String filePath);

    @Modifying
    @Query("DELETE FROM RepoEmbedding r WHERE r.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
