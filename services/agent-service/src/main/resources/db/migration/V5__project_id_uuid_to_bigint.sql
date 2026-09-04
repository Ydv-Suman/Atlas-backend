-- Change project_id from UUID to BIGINT to match workspace-service IDs

ALTER TABLE agent_jobs ALTER COLUMN project_id TYPE BIGINT USING NULL;
ALTER TABLE repo_embeddings ALTER COLUMN project_id TYPE BIGINT USING NULL;
ALTER TABLE api_catalog ALTER COLUMN project_id TYPE BIGINT USING NULL;
