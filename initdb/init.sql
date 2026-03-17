CREATE DATABASE auth_db;
CREATE DATABASE project_db;
CREATE DATABASE ai_db;
CREATE DATABASE preview_db;


-- 2. Switch to the ai_db to perform actions inside it
\c ai_db

-- 3. Now that we are connected to ai_db, enable the vector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Now create your table
CREATE TABLE IF NOT EXISTS document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id VARCHAR(255),
    content TEXT,
    embedding VECTOR(1024)
);

CREATE INDEX ON document_embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);