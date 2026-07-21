package com.atlas.agent_service.repository;

import com.atlas.agent_service.entity.UserAgentKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAgentKeyRepository extends JpaRepository<UserAgentKey, UUID> {

    List<UserAgentKey> findByUserId(UUID userId);

    Optional<UserAgentKey> findByUserIdAndProvider(UUID userId, String provider);

    void deleteByUserIdAndProvider(UUID userId, String provider);
}
