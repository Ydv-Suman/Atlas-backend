package com.atlas.github_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
public class GithubConnections {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Size(max=50)
    @NotNull
    @Column(name = "github_username", nullable = false)
    private String githubUsername;

    @Size(max=255)
    @NotNull
    @Column(name = "encrypted_access_token", nullable = false)
    private String encryptedAccessToken;

    @Size(max=255)
    @NotNull
    @Column(name="scope", nullable = false)
    private String scope;

    @Column(name="authorized_at", nullable = false)
    private Instant authorizedAt;

}
