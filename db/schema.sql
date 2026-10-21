-- Schema for rag-demo (PostgreSQL + pgvector).
-- Idempotent: safe to run more than once.
-- The app uses spring.jpa.hibernate.ddl-auto=validate, so this script must
-- match the DocumentChunk entity.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT         NOT NULL,
    -- bge-m3 embeddings have 1024 dimensions
    embedding   vector(1024),
    source      VARCHAR(255),
    chunk_index INTEGER
);

-- Re-ingestion deletes and replaces chunks by document.
CREATE INDEX IF NOT EXISTS idx_document_chunks_source
    ON document_chunks (source);

-- Approximate nearest-neighbour index for cosine distance (<=>).
-- With a few dozen chunks PostgreSQL may still prefer a sequential scan;
-- the index matters once the table grows to thousands of rows.
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
