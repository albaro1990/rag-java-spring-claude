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

-- Historial de mensajes de las conversaciones.
CREATE TABLE IF NOT EXISTS chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(100) NOT NULL,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_chat_messages_role
    CHECK (role IN ('USER', 'ASSISTANT'))
);

-- Permite recuperar los mensajes de una sesión en orden cronológico.
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created
    ON chat_messages (session_id, created_at, id);

CREATE TABLE IF NOT EXISTS chat_summaries (
    session_id VARCHAR(100) PRIMARY KEY,
    summary TEXT NOT NULL,
    covered_through_message_id BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
    DEFAULT CURRENT_TIMESTAMP
);
