-- Requires the pgvector extension installed in PostgreSQL (run once as superuser if missing):
-- CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS vector;

-- RAG embedding store used by Spring AI PgVectorStore
CREATE TABLE IF NOT EXISTS vector_store (
    id       UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    content  TEXT,
    metadata JSON,
    embedding vector(768)
);

-- HNSW index for fast approximate cosine-similarity search
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
