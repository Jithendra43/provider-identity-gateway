-- V005: Audit tables

CREATE TABLE audit.audit_events (
    event_id            VARCHAR(64)     PRIMARY KEY,
    correlation_id      VARCHAR(128)    NOT NULL,
    event_type          VARCHAR(64)     NOT NULL,
    operation           VARCHAR(32),
    requester_org_id    VARCHAR(64),
    target_org_id       VARCHAR(64),
    outcome             VARCHAR(32)     NOT NULL,
    metadata            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_corr ON audit.audit_events(correlation_id);
CREATE INDEX idx_audit_type ON audit.audit_events(event_type, created_at);
CREATE INDEX idx_audit_requester ON audit.audit_events(requester_org_id, created_at);
