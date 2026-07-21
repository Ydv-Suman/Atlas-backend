CREATE TABLE user_agent_keys (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL,
    provider        VARCHAR(50)     NOT NULL,
    encrypted_key   TEXT            NOT NULL,
    key_hint        VARCHAR(10)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_agent_keys_user_id ON user_agent_keys (user_id);
