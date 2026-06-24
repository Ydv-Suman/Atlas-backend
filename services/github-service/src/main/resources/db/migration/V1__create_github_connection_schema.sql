CREATE TABLE IF NOT EXISTS github_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    github_username VARCHAR(100) NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    scope VARCHAR(255) NOT NULL,
    authorized_at TIMESTAMP NOT NULL DEFAULT NOW()
);