-- pgvector extension (required for RAG embeddings)
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Reference tables ─────────────────────────────────────────────────────────

CREATE TABLE prompt_versions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version     VARCHAR(50)  NOT NULL UNIQUE,
    template    TEXT         NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE model_versions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name    VARCHAR(100) NOT NULL,
    model_version VARCHAR(50)  NOT NULL,
    provider      VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (model_name, model_version)
);

-- ── Core tables ───────────────────────────────────────────────────────────────

CREATE TABLE predictions (
    id                 UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           VARCHAR(255)     NOT NULL,
    recipient_name     VARCHAR(255),
    recipient_relation VARCHAR(100),
    product_name       VARCHAR(255),
    product_category   VARCHAR(100),
    order_date         VARCHAR(50),
    gift_message       TEXT,
    predicted_occasion VARCHAR(50)      NOT NULL,
    confidence_score   DOUBLE PRECISION NOT NULL,
    reason             TEXT,
    evidence           TEXT,
    prediction_source  VARCHAR(100),
    prompt_version_id  UUID REFERENCES prompt_versions (id),
    model_version_id   UUID REFERENCES model_versions (id),
    created_at         TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE prediction_audit (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id    UUID      NOT NULL REFERENCES predictions (id),
    raw_prompt       TEXT,
    raw_response     TEXT,
    model_parameters TEXT,
    rag_chunk_ids    TEXT,
    latency_ms       BIGINT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── RAG vector store ──────────────────────────────────────────────────────────

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding vector(768)
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

CREATE INDEX idx_predictions_order_id
    ON predictions (order_id);

CREATE INDEX idx_prediction_audit_prediction_id
    ON prediction_audit (prediction_id);

CREATE INDEX vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ── Seed data ─────────────────────────────────────────────────────────────────

INSERT INTO prompt_versions (version, template, description, active)
VALUES ('v1',
        'You are a gift occasion classifier with deep knowledge of gifting occasions.

Analyze the provided gift order using the retrieved context rules below, then predict the occasion.

Respond with ONLY this JSON object — no explanation, no markdown, no extra text:
{"occasion":"OCCASION_NAME","confidence":0.85,"reason":"One sentence explaining the prediction.","evidence":["signal 1","signal 2"]}

Rules:
- occasion must be exactly one of: BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY, FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN
- confidence must be a decimal between 0.0 and 1.0
- Use UNKNOWN with confidence below 0.4 when information is insufficient
- reason must be a single sentence
- evidence must be a JSON array of 1-3 short strings citing signals from the order and retrieved rules',
        'RAG-aware system instructions — order details appended at runtime by RagPromptBuilder',
        TRUE);

INSERT INTO model_versions (model_name, model_version, provider, active)
VALUES ('llama3', 'latest', 'OLLAMA', TRUE);
