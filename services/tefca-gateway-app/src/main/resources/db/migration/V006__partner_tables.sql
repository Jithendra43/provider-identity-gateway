-- V006: Partner management tables for the ingress schema
-- Stores per-partner configuration: certificates, OAuth, rate limits

CREATE SCHEMA IF NOT EXISTS ingress;

-- Master partner record
CREATE TABLE ingress.partners (
    partner_id      VARCHAR(64)     PRIMARY KEY,
    org_id          VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(256)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    environment     VARCHAR(32)     NOT NULL DEFAULT 'PRODUCTION',
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_partners_org ON ingress.partners(org_id);
CREATE INDEX idx_partners_status ON ingress.partners(status);

-- Partner mTLS certificates
CREATE TABLE ingress.partner_certificates (
    certificate_id  VARCHAR(64)     PRIMARY KEY,
    partner_id      VARCHAR(64)     NOT NULL REFERENCES ingress.partners(partner_id),
    thumbprint      VARCHAR(128)    NOT NULL UNIQUE,
    subject_dn      VARCHAR(512)    NOT NULL,
    issuer_dn       VARCHAR(512),
    serial_number   VARCHAR(128),
    not_before      TIMESTAMPTZ     NOT NULL,
    not_after       TIMESTAMPTZ     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_partner_certs_partner ON ingress.partner_certificates(partner_id);
CREATE INDEX idx_partner_certs_thumb ON ingress.partner_certificates(thumbprint);

-- Partner OAuth configuration
CREATE TABLE ingress.partner_oauth_config (
    config_id       VARCHAR(64)     PRIMARY KEY,
    partner_id      VARCHAR(64)     NOT NULL UNIQUE REFERENCES ingress.partners(partner_id),
    client_id       VARCHAR(256)    NOT NULL,
    allowed_scopes  TEXT[]          NOT NULL DEFAULT '{}',
    token_ttl_sec   INTEGER         NOT NULL DEFAULT 3600,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_partner_oauth_partner ON ingress.partner_oauth_config(partner_id);

-- Per-partner rate limits (overrides global defaults)
CREATE TABLE ingress.partner_rate_limits (
    rate_limit_id       VARCHAR(64)     PRIMARY KEY,
    partner_id          VARCHAR(64)     NOT NULL UNIQUE REFERENCES ingress.partners(partner_id),
    requests_per_minute INTEGER         NOT NULL DEFAULT 100,
    burst_capacity      INTEGER         NOT NULL DEFAULT 150,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_partner_rl_partner ON ingress.partner_rate_limits(partner_id);

-- Add patient_id_hash column to audit for PHI-safe tracing
ALTER TABLE audit.audit_events
    ADD COLUMN IF NOT EXISTS patient_id_hash VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_audit_patient_hash ON audit.audit_events(patient_id_hash);
