LOCK TABLE repo_embeddings IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF (SELECT format_type(atttypid, atttypmod)
        FROM pg_attribute
        WHERE attrelid = 'repo_embeddings'::regclass AND attname = 'embedding') <> 'vector(768)' THEN
        IF EXISTS (SELECT 1 FROM repo_embeddings) THEN
            RAISE EXCEPTION 'Embedding dimensions differ from the application. Reindex existing embeddings before migrating.';
        END IF;
        ALTER TABLE repo_embeddings ALTER COLUMN embedding TYPE vector(768);
    END IF;
END $$;
