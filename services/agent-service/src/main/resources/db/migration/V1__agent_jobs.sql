CREATE TABLE agent_jobs (
    id                UUID        PRIMARY KEY,
    project_id        UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    prompt            TEXT        NOT NULL,
    agent_provider    VARCHAR(50) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    diff_output       TEXT,
    credits_consumed  INTEGER,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);

CREATE INDEX idx_agent_jobs_user_id ON agent_jobs (user_id);
CREATE INDEX idx_agent_jobs_project_id ON agent_jobs (project_id);
CREATE INDEX idx_agent_jobs_status ON agent_jobs (status);
