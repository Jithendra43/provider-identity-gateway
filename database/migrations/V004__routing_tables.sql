-- V004: Routing tables

CREATE TABLE routing.transaction_log (
    transaction_id      BIGSERIAL       PRIMARY KEY,
    correlation_id      VARCHAR(128)    NOT NULL,
    idempotency_key     VARCHAR(256)    UNIQUE,
    operation           VARCHAR(32)     NOT NULL,
    modality            VARCHAR(32)     NOT NULL,
    requester_org_id    VARCHAR(64)     NOT NULL,
    target_org_id       VARCHAR(64)     NOT NULL,
    resolved_endpoint   VARCHAR(512),
    http_status         INT,
    routing_duration_ms BIGINT,
    forward_duration_ms BIGINT,
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    error_message       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_txn_log_corr ON routing.transaction_log(correlation_id);
CREATE INDEX idx_txn_log_idempotency ON routing.transaction_log(idempotency_key);
CREATE INDEX idx_txn_log_created ON routing.transaction_log(created_at);
