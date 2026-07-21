package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentJobRepository extends JpaRepository<AgentJob, UUID> {

    List<AgentJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AgentJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<AgentJob> findByUserIdAndStatus(UUID userId, JobStatus status);
}
