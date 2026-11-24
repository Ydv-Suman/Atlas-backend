ALTER TABLE agent_jobs ADD COLUMN IF NOT EXISTS events_json TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS agent_jobs_one_active_turn ON agent_jobs(user_id, project_id) WHERE status IN ('PENDING','RUNNING');
