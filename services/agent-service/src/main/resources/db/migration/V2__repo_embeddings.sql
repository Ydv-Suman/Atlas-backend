CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE repo_embeddings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID        NOT NULL,
    file_path     TEXT        NOT NULL,
    chunk_index   INT         NOT NULL,
    content       TEXT        NOT NULL,
    embedding     vector(768) NOT NULL,
    commit_hash   VARCHAR(40),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (project_id, file_path, chunk_index)
);

CREATE INDEX idx_repo_embeddings_project ON repo_embeddings (project_id);
CREATE INDEX idx_repo_embeddings_file    ON repo_embeddings (project_id, file_path);
