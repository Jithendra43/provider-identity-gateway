-- V003: Policy tables

CREATE TABLE policy.policy_rules (
    rule_id             VARCHAR(64)     PRIMARY KEY,
    rule_name           VARCHAR(255)    NOT NULL,
    category            VARCHAR(64)     NOT NULL,
    description         TEXT,
    rule_expression     TEXT            NOT NULL,
    priority            INT             NOT NULL DEFAULT 100,
    active              BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE policy.policy_rule_versions (
    version_id          BIGSERIAL       PRIMARY KEY,
    rule_id             VARCHAR(64)     NOT NULL REFERENCES policy.policy_rules(rule_id),
    version_number      INT             NOT NULL,
    rule_expression     TEXT            NOT NULL,
    changed_by          VARCHAR(128),
    change_reason       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (rule_id, version_number)
);

CREATE TABLE policy.policy_audit_entries (
    audit_id            BIGSERIAL       PRIMARY KEY,
    correlation_id      VARCHAR(128)    NOT NULL,
    requester_org_id    VARCHAR(64)     NOT NULL,
    target_org_id       VARCHAR(64),
    operation           VARCHAR(32)     NOT NULL,
    exchange_purpose    VARCHAR(64)     NOT NULL,
    decision            VARCHAR(16)     NOT NULL,
    policy_version      VARCHAR(32),
    explanation_json    JSONB,
    evaluated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_audit_corr ON policy.policy_audit_entries(correlation_id);
CREATE INDEX idx_policy_audit_requester ON policy.policy_audit_entries(requester_org_id, evaluated_at);
