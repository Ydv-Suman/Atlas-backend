CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE repo_embeddings (
    id            UUID            PRIMARY KEY,
    project_id    UUID            NOT NULL,
    file_path     VARCHAR(500)    NOT NULL,
    chunk_index   INTEGER         NOT NULL,
    content       TEXT            NOT NULL,
    embedding     vector(1536)    NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_repo_embeddings_project_id ON repo_embeddings (project_id);
