package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.AgentJob;
import com.atlas.agent_service.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentJobRepository extends JpaRepository<AgentJob, UUID> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update AgentJob j set j.eventsJson = :events where j.id = :id")
    void updateEvents(@org.springframework.data.repository.query.Param("id") UUID id,
                      @org.springframework.data.repository.query.Param("events") String events);

    boolean existsByProjectIdAndUserIdAndStatusIn(Long projectId, UUID userId, java.util.Collection<JobStatus> statuses);
    List<AgentJob> findByStatusIn(java.util.Collection<JobStatus> statuses);

    List<AgentJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AgentJob> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<AgentJob> findByUserIdAndStatus(UUID userId, JobStatus status);
}
