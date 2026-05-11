-- V002: Directory tables

CREATE TABLE directory.directory_organizations (
    org_id              VARCHAR(64)     PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    oid                 VARCHAR(128)    NOT NULL UNIQUE,
    org_type            VARCHAR(32),
    active              BOOLEAN         NOT NULL DEFAULT true,
    home_community_id   VARCHAR(128),
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE directory.directory_nodes (
    node_id             VARCHAR(64)     PRIMARY KEY,
    org_id              VARCHAR(64)     NOT NULL REFERENCES directory.directory_organizations(org_id),
    name                VARCHAR(255)    NOT NULL,
    home_community_id   VARCHAR(128),
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE directory.directory_endpoints (
    endpoint_id         VARCHAR(64)     PRIMARY KEY,
    node_id             VARCHAR(64)     NOT NULL REFERENCES directory.directory_nodes(node_id),
    url                 VARCHAR(512)    NOT NULL,
    modality            VARCHAR(32)     NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT true,
    certificate_alias   VARCHAR(128),
    supported_operations VARCHAR(512),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE directory.directory_capabilities (
    capability_id       VARCHAR(64)     PRIMARY KEY,
    node_id             VARCHAR(64)     NOT NULL REFERENCES directory.directory_nodes(node_id),
    modality            VARCHAR(32)     NOT NULL,
    operation           VARCHAR(64)     NOT NULL,
    enabled             BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_cap_node_modality_op UNIQUE (node_id, modality, operation)
);

CREATE TABLE directory.directory_snapshots (
    snapshot_id         BIGSERIAL       PRIMARY KEY,
    version_label       VARCHAR(64)     NOT NULL UNIQUE,
    org_count           INTEGER         NOT NULL DEFAULT 0,
    node_count          INTEGER         NOT NULL DEFAULT 0,
    endpoint_count      INTEGER         NOT NULL DEFAULT 0,
    capability_count    INTEGER         NOT NULL DEFAULT 0,
    status              VARCHAR(32)     NOT NULL DEFAULT 'IN_PROGRESS',
    source_url          VARCHAR(512),
    error_message       VARCHAR(2000),
    started_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dir_nodes_org_id ON directory.directory_nodes(org_id);
CREATE INDEX idx_dir_nodes_status ON directory.directory_nodes(status);
CREATE INDEX idx_dir_endpoints_node_id ON directory.directory_endpoints(node_id);
CREATE INDEX idx_dir_endpoints_modality ON directory.directory_endpoints(modality, active);
CREATE INDEX idx_dir_capabilities_node_id ON directory.directory_capabilities(node_id);
CREATE INDEX idx_dir_snapshots_status ON directory.directory_snapshots(status);
CREATE INDEX idx_dir_snapshots_created ON directory.directory_snapshots(created_at DESC);
