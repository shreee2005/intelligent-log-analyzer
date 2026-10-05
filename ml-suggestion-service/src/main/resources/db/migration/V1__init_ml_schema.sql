-- V1__init_ml_schema.sql
-- Initial schema for ml-suggestion-service

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Table for ML suggestions (analysis results)
CREATE TABLE IF NOT EXISTS ml_suggestions (
    id              BIGSERIAL PRIMARY KEY,
    suggestion_id   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    anomaly_id      VARCHAR(128) NOT NULL,
    project_id      BIGINT       NOT NULL,
    service_id      VARCHAR(255) NOT NULL,
    trace_id        VARCHAR(128),

    -- LLM metadata
    model           VARCHAR(100) NOT NULL,
    model_version   VARCHAR(50),
    prompt_tokens   INT,
    completion_tokens INT,
    total_tokens    INT,
    estimated_cost_usd NUMERIC(10,6),
    latency_ms      BIGINT,

    -- Structured output (JSONB)
    hypotheses      JSONB       NOT NULL,
    evidence_log_ids JSONB      NOT NULL,
    confidence      NUMERIC(3,2) NOT NULL,
    recommended_actions JSONB   NOT NULL,
    verification_steps JSONB    NOT NULL,
    uncertainty     JSONB       NOT NULL,
    rag_sources     JSONB       NOT NULL,

    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', -- PROCESSING, COMPLETED, FAILED
    error_message   TEXT,

    -- Timestamps
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ml_suggestions_anomaly_id ON ml_suggestions (anomaly_id);
CREATE INDEX IF NOT EXISTS idx_ml_suggestions_project_service ON ml_suggestions (project_id, service_id);
CREATE INDEX IF NOT EXISTS idx_ml_suggestions_created_at ON ml_suggestions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ml_suggestions_status ON ml_suggestions (status);

-- Table for user feedback on suggestions (Phase 11 prep)
CREATE TABLE IF NOT EXISTS ml_suggestion_feedback (
    id              BIGSERIAL PRIMARY KEY,
    suggestion_id   BIGINT       NOT NULL REFERENCES ml_suggestions(id) ON DELETE CASCADE,
    rating          VARCHAR(20)  NOT NULL, -- ACCEPTED, REJECTED, EDITED
    corrected_action TEXT,
    comment         TEXT,
    user_email      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ml_feedback_suggestion ON ml_suggestion_feedback (suggestion_id);
CREATE INDEX IF NOT EXISTS idx_ml_feedback_user ON ml_suggestion_feedback (user_email);

-- Table for RAG incident embeddings
CREATE TABLE IF NOT EXISTS incident_embeddings (
    id                  BIGSERIAL PRIMARY KEY,
    incident_id         VARCHAR(100) NOT NULL UNIQUE,
    title               VARCHAR(500) NOT NULL,
    description         TEXT         NOT NULL,
    resolution          TEXT,
    service_patterns    TEXT[],
    error_patterns      TEXT[],
    embedding           vector(1536),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- HNSW index for fast vector similarity search
CREATE INDEX IF NOT EXISTS idx_incident_embeddings_vector
    ON incident_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_incident_embeddings_service_patterns
    ON incident_embeddings USING GIN (service_patterns);

CREATE INDEX IF NOT EXISTS idx_incident_embeddings_error_patterns
    ON incident_embeddings USING GIN (error_patterns);

-- Table for async request tracking (optional - for monitoring)
CREATE TABLE IF NOT EXISTS ml_analysis_requests (
    id              BIGSERIAL PRIMARY KEY,
    request_id      UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    anomaly_id      VARCHAR(128) NOT NULL,
    project_id      BIGINT       NOT NULL,
    service_id      VARCHAR(255) NOT NULL,
    trace_id        VARCHAR(128),
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED', -- QUEUED, PROCESSING, COMPLETED, FAILED
    suggestion_id   BIGINT       REFERENCES ml_suggestions(id),
    error_message   TEXT,
    retry_count     INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ml_requests_anomaly ON ml_analysis_requests (anomaly_id);
CREATE INDEX IF NOT EXISTS idx_ml_requests_status ON ml_analysis_requests (status);
CREATE INDEX IF NOT EXISTS idx_ml_requests_created ON ml_analysis_requests (created_at DESC);