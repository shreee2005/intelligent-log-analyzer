-- V1__init_alert_schema.sql
-- Initial schema for alert-service: webhook subscriptions and deliveries

CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    secret          VARCHAR(256)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_project_active
    ON webhook_subscriptions (project_id, active);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT       NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_id            VARCHAR(128) NOT NULL,
    payload             TEXT         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts            INT          NOT NULL DEFAULT 0,
    http_status         INT,
    latency_ms          BIGINT,
    next_attempt_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMP,
    last_error          TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_webhook_delivery_event_subscription
    ON webhook_deliveries (subscription_id, event_id);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status_next_attempt
    ON webhook_deliveries (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_subscription_created
    ON webhook_deliveries (subscription_id, created_at DESC);

CREATE TABLE IF NOT EXISTS webhook_dlq (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT       NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_id            VARCHAR(128) NOT NULL,
    payload             TEXT         NOT NULL,
    attempts            INT          NOT NULL,
    last_http_status    INT,
    last_error          TEXT,
    failed_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    original_created_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_webhook_dlq_subscription_failed
    ON webhook_dlq (subscription_id, failed_at DESC);