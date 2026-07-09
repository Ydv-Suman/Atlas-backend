package com.atlas.workspace_service.repository;

import com.atlas.workspace_service.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository <WorkspaceEntity, Long>{
    Optional<WorkspaceEntity> findById(Long id);
    Optional<WorkspaceEntity> findByUserId(Long userId);
    Optional<WorkspaceEntity> findByProjectName(String projectName);
}
