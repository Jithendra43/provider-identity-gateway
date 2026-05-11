-- Seed data: sample organizations, nodes, endpoints for local development

INSERT INTO directory.directory_organizations (org_id, name, oid, org_type, active, home_community_id)
VALUES
    ('ORG-QHIN-001', 'CommonWell Health Alliance', '2.16.840.1.113883.3.6147', 'QHIN', true, 'urn:oid:2.16.840.1.113883.3.6147'),
    ('ORG-QHIN-002', 'eHealth Exchange', '2.16.840.1.113883.3.6037', 'QHIN', true, 'urn:oid:2.16.840.1.113883.3.6037'),
    ('ORG-SUB-001', 'Sample Hospital System', '2.16.840.1.113883.3.9999', 'SUB_PARTICIPANT', true, 'urn:oid:2.16.840.1.113883.3.9999')
ON CONFLICT (org_id) DO NOTHING;

INSERT INTO directory.directory_nodes (node_id, org_id, name, home_community_id, status)
VALUES
    ('NODE-CW-001', 'ORG-QHIN-001', 'CommonWell Primary Node', 'urn:oid:2.16.840.1.113883.3.6147.1', 'ACTIVE'),
    ('NODE-EHX-001', 'ORG-QHIN-002', 'eHealth Exchange Primary Node', 'urn:oid:2.16.840.1.113883.3.6037.1', 'ACTIVE'),
    ('NODE-HOSP-001', 'ORG-SUB-001', 'Sample Hospital Node', 'urn:oid:2.16.840.1.113883.3.9999.1', 'ACTIVE')
ON CONFLICT (node_id) DO NOTHING;

INSERT INTO directory.directory_endpoints (endpoint_id, node_id, url, modality, active)
VALUES
    ('EP-CW-XCPD', 'NODE-CW-001', 'https://gateway.commonwell.local/xcpd', 'XCPD', true),
    ('EP-CW-XCA-Q', 'NODE-CW-001', 'https://gateway.commonwell.local/xca/query', 'XCA_QUERY', true),
    ('EP-CW-XCA-R', 'NODE-CW-001', 'https://gateway.commonwell.local/xca/retrieve', 'XCA_RETRIEVE', true),
    ('EP-EHX-XCPD', 'NODE-EHX-001', 'https://gateway.ehealthexchange.local/xcpd', 'XCPD', true),
    ('EP-EHX-FHIR', 'NODE-EHX-001', 'https://gateway.ehealthexchange.local/fhir', 'FHIR', true),
    ('EP-HOSP-XDR', 'NODE-HOSP-001', 'https://gateway.hospital.local/xdr', 'XDR', true)
ON CONFLICT (endpoint_id) DO NOTHING;
