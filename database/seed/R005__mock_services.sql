-- R005: Mock IdP / Mock FHIR partner so a freshly-built environment can do
-- a complete loopback round-trip with no external QHIN dependency. The mock
-- IdP and mock FHIR endpoints are served by ingress-auth-service itself
-- (see MockIdpController, MockFhirController) on the same Fargate task.

-- ── Directory ────────────────────────────────────────────────────────────
INSERT INTO directory.directory_organizations
        (org_id,         name,                    oid,                              org_type, active, home_community_id)
VALUES  ('ORG-MOCK-001', 'Mock TEFCA Partner',    '2.16.840.1.113883.3.999999',     'QHIN',   TRUE,   '2.16.840.1.113883.3.999999.1')
ON CONFLICT (org_id) DO NOTHING;

INSERT INTO directory.directory_nodes
        (node_id,        org_id,         name,                       home_community_id,                status)
VALUES  ('NODE-MOCK-001','ORG-MOCK-001', 'Mock Loopback Node',       '2.16.840.1.113883.3.999999.1',   'ACTIVE')
ON CONFLICT (node_id) DO NOTHING;

INSERT INTO directory.directory_endpoints
        (endpoint_id,        node_id,         url,                                    modality,    active, certificate_alias)
VALUES  ('EP-MOCK-FHIR',     'NODE-MOCK-001', 'http://127.0.0.1:8080/mock-fhir',      'FHIR',      TRUE,   'mock-partner'),
        ('EP-MOCK-XCPD',     'NODE-MOCK-001', 'http://127.0.0.1:8080/mock-fhir/xcpd', 'XCPD',      TRUE,   'mock-partner'),
        ('EP-MOCK-XCA-Q',    'NODE-MOCK-001', 'http://127.0.0.1:8080/mock-fhir/xca-q','XCA_QUERY', TRUE,   'mock-partner'),
        ('EP-MOCK-XCA-R',    'NODE-MOCK-001', 'http://127.0.0.1:8080/mock-fhir/xca-r','XCA_RETRIEVE',TRUE, 'mock-partner')
ON CONFLICT (endpoint_id) DO NOTHING;

-- ── Partner / OAuth ──────────────────────────────────────────────────────
INSERT INTO ingress.partners
        (partner_id,     org_id,         name,                  status,   environment,   metadata)
VALUES  ('PART-MOCK-001','ORG-MOCK-001', 'Mock TEFCA Partner',  'ACTIVE', 'PRODUCTION',  '{"loopback":true}'::jsonb)
ON CONFLICT (partner_id) DO NOTHING;

INSERT INTO ingress.partner_oauth_config
        (config_id,         partner_id,      client_id,            allowed_scopes,             token_ttl_sec, active)
VALUES  ('OAUTH-MOCK-001',  'PART-MOCK-001', 'mock-partner-client', ARRAY['system/*.read'],    3600,          TRUE)
ON CONFLICT (config_id) DO NOTHING;
