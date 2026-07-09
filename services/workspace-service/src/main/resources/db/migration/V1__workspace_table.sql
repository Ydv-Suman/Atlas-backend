CREATE TABLE IF NOT EXISTS workspaces (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    github_url      VARCHAR(512),
    repo_owner      VARCHAR(255),
    repo_name       VARCHAR(255),
    default_branch  VARCHAR(255) DEFAULT 'main',
    repo_visibility VARCHAR(20)  DEFAULT 'PUBLIC',
    ownership       VARCHAR(20)  DEFAULT 'OWNER',
    framework       VARCHAR(100),
    project_type    VARCHAR(50),
    last_synced_at  TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspaces_user_id ON workspaces(user_id);
