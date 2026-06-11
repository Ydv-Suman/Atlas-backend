package com.atlas.auth_service.repository;

import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AtlasUserRespsitory extends JpaRepository<AtlasUsers, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    Optional<AtlasUsers> findByUsername(String username);

    Optional<AtlasUsers> findByEmail(String email);
}
