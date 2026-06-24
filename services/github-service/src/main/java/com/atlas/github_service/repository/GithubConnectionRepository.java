package com.atlas.github_service.repository;

import com.atlas.github_service.entity.GithubConnections;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubConnectionRepository extends JpaRepository<GithubConnections, UUID> {

    Optional<GithubConnections> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
