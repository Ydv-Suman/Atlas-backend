CREATE TABLE api_catalog (
    id            UUID            PRIMARY KEY,
    project_id    UUID            NOT NULL,
    endpoints     JSONB           NOT NULL,
    commit_hash   VARCHAR(40)     NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_catalog_project_id ON api_catalog (project_id);
