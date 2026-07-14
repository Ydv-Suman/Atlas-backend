CREATE TABLE workspace (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         VARCHAR(255)    NOT NULL,
    project_name    VARCHAR(150)    NOT NULL,
    framework       VARCHAR(255),
    github_url      VARCHAR(500)    NOT NULL,
    repo_owner      VARCHAR(150)    NOT NULL,
    repo_ownership  VARCHAR(50),
    repo_visibility VARCHAR(50),
    project_type    VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_synched_at TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_workspace_user_id ON workspace (user_id);
