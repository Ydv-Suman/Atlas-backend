package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.ApiCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiCatalogRepository extends JpaRepository<ApiCatalog, UUID> {

    Optional<ApiCatalog> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
