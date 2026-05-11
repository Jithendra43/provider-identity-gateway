-- ───────────────────────────────────────────────────────────────────────────
-- V008__bootstrap_directory_endpoints.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Idempotent guarantee that the directory.directory_endpoints table contains
-- the minimum endpoints needed for routing. V007 seeded organizations and
-- nodes, but the Test Console's PATIENT_DISCOVERY → ORG-QHIN-001 path needs
-- an XCPD endpoint or RoutingService throws "No endpoint found".
--
-- Mirrors R001__seed_directory_data.sql endpoint inserts. Repeatable
-- migrations (R001) are skipped when the gateway-app's Flyway runs without a
-- `schemas` setting and the table already exists from a prior baseline, so we
-- materialize the same rows as a versioned migration.
-- ───────────────────────────────────────────────────────────────────────────

INSERT INTO directory.directory_endpoints (endpoint_id, node_id, url, modality, active)
VALUES
    ('EP-CW-XCPD',    'NODE-CW-001',   'https://gateway.commonwell.local/xcpd',         'XCPD',         true),
    ('EP-CW-XCA-Q',   'NODE-CW-001',   'https://gateway.commonwell.local/xca/query',    'XCA_QUERY',    true),
    ('EP-CW-XCA-R',   'NODE-CW-001',   'https://gateway.commonwell.local/xca/retrieve', 'XCA_RETRIEVE', true),
    ('EP-CW-FHIR',    'NODE-CW-001',   'https://gateway.commonwell.local/fhir',         'FHIR',         true),
    ('EP-EHX-XCPD',   'NODE-EHX-001',  'https://gateway.ehealthexchange.local/xcpd',    'XCPD',         true),
    ('EP-EHX-FHIR',   'NODE-EHX-001',  'https://gateway.ehealthexchange.local/fhir',    'FHIR',         true),
    ('EP-EHX-XCA-Q',  'NODE-EHX-001',  'https://gateway.ehealthexchange.local/xca/query',    'XCA_QUERY',    true),
    ('EP-EHX-XCA-R',  'NODE-EHX-001',  'https://gateway.ehealthexchange.local/xca/retrieve', 'XCA_RETRIEVE', true),
    ('EP-HOSP-XDR',   'NODE-HOSP-001', 'https://gateway.hospital.local/xdr',            'XDR',          true),
    ('EP-HOSP-FHIR',  'NODE-HOSP-001', 'https://gateway.hospital.local/fhir',           'FHIR',         true)
ON CONFLICT (endpoint_id) DO NOTHING;
