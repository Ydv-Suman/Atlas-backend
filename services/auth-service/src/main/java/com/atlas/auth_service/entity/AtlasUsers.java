package com.atlas.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "users")
public class AtlasUsers {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Size(max=50)
    @NotNull
    @Column(name="first_name", nullable = false)
    private String firstName;

    @Size(max=50)
    @Column(name="middle_name")
    private String middleName;

    @Size(max=50)
    @NotNull
    @Column(name="last_name", nullable = false)
    private String lastName;

    @Size(min=5, max=50)
    @NotNull
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Size(max=100)
    @NotNull
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @NotNull
    private UserRole role = UserRole.ROLE_USER;

    @Size(max=200)
    @NotNull
    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "github_authorized", nullable = false)
    private boolean githubAuthorized;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    @NotNull
    private UserTier tier = UserTier.FREE;

    @Column(name = "credits", nullable = false)
    private int credits = 50;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
