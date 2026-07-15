-- Convert user_id from varchar (storing username) to UUID (storing actual user ID)
-- First drop existing data since it contains usernames, not UUIDs
TRUNCATE TABLE github_connections;

ALTER TABLE github_connections
    ALTER COLUMN user_id TYPE UUID USING user_id::UUID;

ALTER TABLE github_connections
    ADD CONSTRAINT fk_github_connections_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
