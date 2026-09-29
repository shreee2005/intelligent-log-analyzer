CREATE TABLE IF NOT EXISTS custom_metrics (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name          VARCHAR(255)  NOT NULL,
    regex_pattern VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);