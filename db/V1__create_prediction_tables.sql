-- Reference copy. Actual migration is in src/main/resources/db/migration/

CREATE TABLE prompt_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version     VARCHAR(50)  NOT NULL UNIQUE,
    template    TEXT         NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE model_versions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name    VARCHAR(100) NOT NULL,
    model_version VARCHAR(50)  NOT NULL,
    provider      VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (model_name, model_version)
);

CREATE TABLE predictions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           VARCHAR(255)      NOT NULL,
    recipient_name     VARCHAR(255),
    recipient_relation VARCHAR(100),
    product_name       VARCHAR(255),
    product_category   VARCHAR(100),
    order_date         VARCHAR(50),
    gift_message       TEXT,
    predicted_occasion VARCHAR(50)       NOT NULL,
    confidence_score   DOUBLE PRECISION  NOT NULL,
    reason             TEXT,
    prediction_source  VARCHAR(100),
    prompt_version_id  UUID REFERENCES prompt_versions (id),
    model_version_id   UUID REFERENCES model_versions (id),
    created_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE prediction_audit (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id UUID      NOT NULL REFERENCES predictions (id),
    raw_prompt    TEXT,
    raw_response  TEXT,
    latency_ms    BIGINT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_predictions_order_id ON predictions (order_id);
CREATE INDEX idx_prediction_audit_prediction_id ON prediction_audit (prediction_id);
