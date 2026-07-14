package com.atlas.workspace_service.repository;

import com.atlas.workspace_service.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, Long> {
    List<WorkspaceEntity> findAllByUserId(String userId);
    Optional<WorkspaceEntity> findByIdAndUserId(Long id, String userId);
    boolean existsByUserIdAndGithubUrl(String userId, String githubUrl);
}
