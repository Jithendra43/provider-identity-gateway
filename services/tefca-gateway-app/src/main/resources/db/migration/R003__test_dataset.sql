-- R003: Synthetic test dataset for local development & e2e tests.
-- Adds more organizations/nodes/endpoints, a baseline set of policy rules,
-- and a sample partner registry so the admin UI lights up with real data.
--
-- Re-runnable: every INSERT is guarded with ON CONFLICT DO NOTHING / UPDATE.

-- ─── Extra organizations & nodes ────────────────────────────────────────────
INSERT INTO directory.directory_organizations
    (org_id, name, oid, org_type, active, home_community_id)
VALUES
    ('ORG-QHIN-003', 'Epic Nexus QHIN',          '2.16.840.1.113883.3.7204', 'QHIN',            true, 'urn:oid:2.16.840.1.113883.3.7204'),
    ('ORG-SUB-002',  'Northside Clinic Network',  '2.16.840.1.113883.3.8888', 'SUB_PARTICIPANT', true, 'urn:oid:2.16.840.1.113883.3.8888'),
    ('ORG-SUB-003',  'Coastal Pediatrics',        '2.16.840.1.113883.3.7777', 'SUB_PARTICIPANT', true, 'urn:oid:2.16.840.1.113883.3.7777'),
    ('ORG-PUB-001',  'StateLab Public Health',    '2.16.840.1.113883.3.5555', 'PUBLIC_HEALTH',   true, 'urn:oid:2.16.840.1.113883.3.5555')
ON CONFLICT (org_id) DO NOTHING;

INSERT INTO directory.directory_nodes
    (node_id, org_id, name, home_community_id, status)
VALUES
    ('NODE-EPIC-001',  'ORG-QHIN-003', 'Epic Nexus Primary Node',     'urn:oid:2.16.840.1.113883.3.7204.1', 'ACTIVE'),
    ('NODE-NORTH-001', 'ORG-SUB-002',  'Northside Clinic Node',       'urn:oid:2.16.840.1.113883.3.8888.1', 'ACTIVE'),
    ('NODE-COAST-001', 'ORG-SUB-003',  'Coastal Pediatrics Node',     'urn:oid:2.16.840.1.113883.3.7777.1', 'ACTIVE'),
    ('NODE-PHIE-001',  'ORG-PUB-001',  'Public Health Reporting Node','urn:oid:2.16.840.1.113883.3.5555.1', 'ACTIVE')
ON CONFLICT (node_id) DO NOTHING;

INSERT INTO directory.directory_endpoints
    (endpoint_id, node_id, url, modality, active, supported_operations)
VALUES
    ('EP-EPIC-XCPD',  'NODE-EPIC-001',  'http://wiremock:8080/xcpd',         'XCPD',         true, 'PATIENT_DISCOVERY'),
    ('EP-EPIC-XCA-Q', 'NODE-EPIC-001',  'http://wiremock:8080/xca/query',    'XCA_QUERY',    true, 'DOCUMENT_QUERY'),
    ('EP-EPIC-XCA-R', 'NODE-EPIC-001',  'http://wiremock:8080/xca/retrieve', 'XCA_RETRIEVE', true, 'DOCUMENT_RETRIEVE'),
    ('EP-NORTH-FHIR', 'NODE-NORTH-001', 'http://wiremock:8080/fhir',         'FHIR',         true, 'PATIENT_DISCOVERY,DOCUMENT_QUERY'),
    ('EP-COAST-XDR',  'NODE-COAST-001', 'http://wiremock:8080/xdr',          'XDR',          true, 'MESSAGE_DELIVERY'),
    ('EP-PHIE-XDR',   'NODE-PHIE-001',  'http://wiremock:8080/xdr',          'XDR',          true, 'MESSAGE_DELIVERY')
ON CONFLICT (endpoint_id) DO NOTHING;

INSERT INTO directory.directory_capabilities
    (capability_id, node_id, modality, operation, enabled)
VALUES
    ('CAP-EPIC-XCPD',  'NODE-EPIC-001',  'XCPD',         'PATIENT_DISCOVERY', true),
    ('CAP-EPIC-XCA-Q', 'NODE-EPIC-001',  'XCA_QUERY',    'DOCUMENT_QUERY',    true),
    ('CAP-EPIC-XCA-R', 'NODE-EPIC-001',  'XCA_RETRIEVE', 'DOCUMENT_RETRIEVE', true),
    ('CAP-NORTH-FHIR', 'NODE-NORTH-001', 'FHIR',         'PATIENT_DISCOVERY', true),
    ('CAP-COAST-XDR',  'NODE-COAST-001', 'XDR',          'MESSAGE_DELIVERY',  true),
    ('CAP-PHIE-XDR',   'NODE-PHIE-001',  'XDR',          'MESSAGE_DELIVERY',  true),
    ('CAP-CW-XCPD',    'NODE-CW-001',    'XCPD',         'PATIENT_DISCOVERY', true),
    ('CAP-CW-XCA-Q',   'NODE-CW-001',    'XCA_QUERY',    'DOCUMENT_QUERY',    true),
    ('CAP-CW-XCA-R',   'NODE-CW-001',    'XCA_RETRIEVE', 'DOCUMENT_RETRIEVE', true),
    ('CAP-EHX-XCPD',   'NODE-EHX-001',   'XCPD',         'PATIENT_DISCOVERY', true),
    ('CAP-EHX-FHIR',   'NODE-EHX-001',   'FHIR',         'PATIENT_DISCOVERY', true),
    ('CAP-HOSP-XDR',   'NODE-HOSP-001',  'XDR',          'MESSAGE_DELIVERY',  true)
ON CONFLICT (node_id, modality, operation) DO NOTHING;

-- ─── HIPAA / 42 CFR Part 2 / TEFCA policy rules ────────────────────────────
-- Authoritative ruleset modelling HIPAA Privacy Rule (45 CFR §164.5xx),
-- HIPAA Security Rule (45 CFR §164.3xx), 42 CFR Part 2 (substance-use
-- disorder records), TEFCA Common Agreement, and operational safeguards.
--
-- The policy-service applies rules sorted by `priority` ASC (lower = earlier).
-- DENY short-circuits evaluation; PERMIT rules accumulate obligations that the
-- ObligationResolver attaches to the routing decision (encryption, audit,
-- digital-signature, redaction, etc.).
--
-- IMPORTANT: the runtime policy engine uses hard-coded Java validators in
-- services/policy-service/src/main/java/.../engine/. The rows here are the
-- system of record for the rules, drive the admin UI, and document each
-- check's regulatory citation; see PolicyEngine.java for the executable form.

INSERT INTO policy.policy_rules
    (rule_id, rule_name, category, description, rule_expression, priority, active)
VALUES
    -- ── 1. HIPAA Privacy Rule §164.508 — uses requiring authorization ──
    ('HIPAA-PR-MARKETING',
        'Prohibit marketing without authorization',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.508(a)(3): A covered entity must obtain a written authorization for any use or disclosure of PHI for marketing. The gateway never has such an authorization on file, so any exchangePurpose=MARKETING is denied.',
        'request.exchangePurpose == "MARKETING" -> DENY (regulatory:45CFR164.508(a)(3))',
        10, true),

    ('HIPAA-PR-SALE-OF-PHI',
        'Prohibit sale of PHI',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.502(a)(5)(ii) & 164.508(a)(4): Disclosure of PHI in exchange for direct or indirect remuneration is a "sale" and requires explicit authorization specifying the remuneration. Denied unconditionally.',
        'request.exchangePurpose == "SALE_OF_PHI" || request.metadata.remuneration == true -> DENY (regulatory:45CFR164.502(a)(5)(ii))',
        11, true),

    ('HIPAA-PR-PSYCHOTHERAPY-NOTES',
        'Restrict psychotherapy notes',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.508(a)(2): Psychotherapy notes require a separate, specific patient authorization. Block any document classified PSYCHOTHERAPY_NOTES unless the request carries a verified consent reference.',
        'request.dataClasses CONTAINS "PSYCHOTHERAPY_NOTES" && request.consentReference == null -> DENY (regulatory:45CFR164.508(a)(2))',
        12, true),

    -- ── 2. 42 CFR Part 2 — substance-use disorder records ──
    ('CFR-PART2-SUD',
        'Substance-use disorder records require Part 2 consent',
        'CFR_PART_2',
        '42 CFR Part 2 §2.31: Records of identity, diagnosis, prognosis, or treatment for substance-use disorder may not be redisclosed without a signed Part 2 consent. Block when SUD data class is present and no Part 2 consent token is attached.',
        'request.dataClasses CONTAINS "SUBSTANCE_USE_DISORDER" && request.consentReference.type != "42CFR_PART_2" -> DENY (regulatory:42CFR2.31)',
        13, true),

    -- ── 3. HIPAA Privacy Rule §164.506 — TPO permitted ──
    ('HIPAA-PR-TPO-PERMITTED',
        'Treatment, Payment, Healthcare Operations are permitted',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.506(c): A covered entity may use or disclose PHI for its own treatment, payment, and healthcare operations without authorization. Permit and attach minimum-necessary + audit obligations.',
        'request.exchangePurpose IN ("TREATMENT","PAYMENT","HEALTHCARE_OPERATIONS") -> PERMIT + obligations:[MINIMUM_NECESSARY, AUDIT_TRAIL_REQUIRED] (regulatory:45CFR164.506(c))',
        100, true),

    ('HIPAA-PR-INDIVIDUAL-ACCESS',
        'Individual right of access',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.524: An individual has the right to access, inspect, and obtain a copy of PHI in a designated record set. Permit when patientId matches the requesting individual; require strong encryption in transit and audit.',
        'request.exchangePurpose == "INDIVIDUAL_ACCESS" && request.patientId != null -> PERMIT + obligations:[END_TO_END_ENCRYPTION, AUDIT_TRAIL_REQUIRED, INDIVIDUAL_AUTHENTICATION] (regulatory:45CFR164.524)',
        110, true),

    -- ── 4. HIPAA Privacy Rule §164.512 — public-interest disclosures ──
    ('HIPAA-PR-PUBLIC-HEALTH',
        'Public-health activity disclosure',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.512(b): Permits disclosure to a public-health authority for collection of disease, injury, or vital event reports. Restricted to PUBLIC_HEALTH-typed targets.',
        'request.exchangePurpose == "PUBLIC_HEALTH" && directory.org(request.targetOrgId).org_type == "PUBLIC_HEALTH" -> PERMIT + obligations:[MINIMUM_NECESSARY, AUDIT_TRAIL_REQUIRED] (regulatory:45CFR164.512(b))',
        120, true),

    ('HIPAA-PR-REQUIRED-BY-LAW',
        'Required by law / judicial / law enforcement',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.512(a),(e),(f): Permits disclosures required by law, in response to a court order, or to law enforcement under specific conditions. Always attach an elevated audit obligation.',
        'request.exchangePurpose IN ("JUDICIAL","LAW_ENFORCEMENT","REQUIRED_BY_LAW") -> PERMIT + obligations:[ELEVATED_AUDIT, LEGAL_BASIS_REQUIRED] (regulatory:45CFR164.512(a)(e)(f))',
        130, true),

    -- ── 5. Emergency / break-the-glass ──
    ('TEFCA-BREAKGLASS',
        'Emergency break-glass override',
        'TEFCA_BREAKGLASS',
        'TEFCA SOP & 45 CFR 164.510(b)(3): Permits emergency disclosure when the patient is incapacitated and the disclosure is in the patient''s best interest. Requires explicit BREAKGLASS purpose, justification, and elevated audit + immediate notification obligations.',
        'request.exchangePurpose == "BREAKGLASS" && request.metadata.breakglassReason != null -> PERMIT + obligations:[ELEVATED_AUDIT, BREAKGLASS_NOTIFICATION, MINIMUM_NECESSARY] (regulatory:45CFR164.510(b)(3))',
        140, true),

    -- ── 6. HIPAA Privacy Rule §164.502(b) — minimum necessary ──
    ('HIPAA-PR-MINIMUM-NECESSARY',
        'Minimum necessary standard',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.502(b) & 164.514(d): Limit disclosed PHI to the minimum necessary to accomplish the intended purpose. Add MINIMUM_NECESSARY obligation to every PERMIT outcome (downstream filters strip non-requested data classes).',
        'effect == PERMIT -> PERMIT + obligation:MINIMUM_NECESSARY (regulatory:45CFR164.502(b))',
        200, true),

    -- ── 7. HIPAA Security Rule technical safeguards ──
    ('HIPAA-SR-TRANSMISSION-SECURITY',
        'Encryption in transit (transmission security)',
        'HIPAA_SECURITY_RULE',
        '45 CFR 164.312(e)(1)-(e)(2)(ii): Implement technical security measures to guard against unauthorized access to e-PHI transmitted over an electronic network. Require TLS 1.2+ and END_TO_END_ENCRYPTION on every PERMIT.',
        'effect == PERMIT -> PERMIT + obligation:END_TO_END_ENCRYPTION (regulatory:45CFR164.312(e))',
        210, true),

    ('HIPAA-SR-INTEGRITY',
        'Integrity controls (digital signature)',
        'HIPAA_SECURITY_RULE',
        '45 CFR 164.312(c)(1)-(c)(2): Implement policies to protect e-PHI from improper alteration or destruction. Clinical document operations (XCA_RETRIEVE, MESSAGE_DELIVERY) must carry a digital signature.',
        'request.operation IN ("DOCUMENT_RETRIEVE","MESSAGE_DELIVERY") -> PERMIT + obligation:DIGITAL_SIGNATURE_REQUIRED (regulatory:45CFR164.312(c))',
        220, true),

    ('HIPAA-SR-ACCESS-CONTROL',
        'Access control & user authentication',
        'HIPAA_SECURITY_RULE',
        '45 CFR 164.312(a)(1)-(a)(2)(i),(d): Verify the identity of the requester (mTLS + OAuth2/JWT) and confirm the requesting node is registered, active, and authorized for the requested operation.',
        'directory.node(request.requesterNodeId).status != "ACTIVE" || request.authenticatedRole == null -> DENY (regulatory:45CFR164.312(a)(d))',
        20, true),

    ('HIPAA-SR-AUDIT-CONTROLS',
        'Audit controls',
        'HIPAA_SECURITY_RULE',
        '45 CFR 164.312(b): Implement hardware, software, and procedural mechanisms that record and examine activity in systems containing e-PHI. Every decision (PERMIT or DENY) must produce an immutable audit_event row.',
        'true -> PERMIT + obligation:AUDIT_TRAIL_REQUIRED (regulatory:45CFR164.312(b))',
        230, true),

    -- ── 8. Partner / TEFCA-Common-Agreement safeguards ──
    ('TEFCA-CA-PARTNER-ACTIVE',
        'Disclosing to inactive partner is prohibited',
        'TEFCA_COMMON_AGREEMENT',
        'TEFCA Common Agreement §6.2 & RCE Directory: A QHIN MUST NOT route to a Participant or Sub-Participant whose directory entry is inactive or suspended.',
        'directory.org(request.targetOrgId).active == false -> DENY (regulatory:TEFCA_CA_6.2)',
        21, true),

    ('TEFCA-CA-BAA-REQUIRED',
        'Business Associate Agreement required for non-QHIN disclosures',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.502(e) & 164.504(e): A covered entity may not disclose PHI to a business associate without a satisfactory BAA. Require partner.metadata.baaOnFile=true for any external (non-QHIN) recipient.',
        'directory.org(request.targetOrgId).org_type != "QHIN" && partner.metadata.baaOnFile != true -> DENY (regulatory:45CFR164.502(e))',
        22, true),

    -- ── 9. PHI-safeguard obligations ──
    ('PHI-SSN-ENCRYPTION',
        'SSN-bearing payloads require enhanced encryption',
        'PHI_SAFEGUARD',
        'NIST 800-66 & HIPAA 164.312(a)(2)(iv): When the patient identifier system is SSN, mandate AES-256 end-to-end encryption and tokenize before logging.',
        'request.patientIdSystem == "SSN" -> PERMIT + obligations:[END_TO_END_ENCRYPTION_AES256, TOKENIZE_BEFORE_LOG] (regulatory:NIST_800-66)',
        240, true),

    ('PHI-SENSITIVE-DATA-CLASSES',
        'Redact specially-protected data classes',
        'PHI_SAFEGUARD',
        'State super-confidential categories (HIV/AIDS, mental health, reproductive health, genetic) require explicit consent or redaction. When such a data class is requested without consent, strip those segments before delivery.',
        'request.dataClasses INTERSECTS ["HIV","MENTAL_HEALTH","REPRODUCTIVE_HEALTH","GENETIC"] && request.consentReference == null -> PERMIT + obligation:REDACT_SENSITIVE_SEGMENTS (regulatory:state_super_confidential_laws)',
        250, true),

    -- ── 10. Operational safeguards ──
    ('OP-RATE-LIMIT',
        'Rate-limit excessive requesters',
        'OPERATIONAL',
        'Defends against denial-of-service and run-away credential abuse. When a requesterOrg exceeds 100 req/min, attach THROTTLE_REQUIRED so the routing layer slows or 429s the request.',
        'rate.requesterRpm(request.requesterOrgId) > 100 -> PERMIT + obligation:THROTTLE_REQUIRED (operational)',
        300, true),

    -- ── 11. Default ──
    ('DEFAULT-DENY',
        'Default deny (fail-closed)',
        'DEFAULT',
        'TEFCA & HIPAA fail-closed posture: any request not matched by an explicit PERMIT rule is denied. This is the residual rule and runs last.',
        'true -> DENY (regulatory:fail_closed_default)',
        9999, true)
ON CONFLICT (rule_id) DO NOTHING;

INSERT INTO policy.policy_rule_versions
    (rule_id, version_number, rule_expression, changed_by, change_reason)
SELECT rule_id, 1, rule_expression, 'system-seed', 'Initial HIPAA-aligned ruleset (R003)'
FROM policy.policy_rules
WHERE rule_id IN ('HIPAA-PR-MARKETING','HIPAA-PR-SALE-OF-PHI','HIPAA-PR-PSYCHOTHERAPY-NOTES','CFR-PART2-SUD','HIPAA-PR-TPO-PERMITTED','HIPAA-PR-INDIVIDUAL-ACCESS','HIPAA-PR-PUBLIC-HEALTH','HIPAA-PR-REQUIRED-BY-LAW','TEFCA-BREAKGLASS','HIPAA-PR-MINIMUM-NECESSARY','HIPAA-SR-TRANSMISSION-SECURITY','HIPAA-SR-INTEGRITY','HIPAA-SR-ACCESS-CONTROL','HIPAA-SR-AUDIT-CONTROLS','TEFCA-CA-PARTNER-ACTIVE','TEFCA-CA-BAA-REQUIRED','PHI-SSN-ENCRYPTION','PHI-SENSITIVE-DATA-CLASSES','OP-RATE-LIMIT','DEFAULT-DENY')
ON CONFLICT (rule_id, version_number) DO NOTHING;

-- ─── Sample policy decision history (drives the Policy Audit page) ──────────
INSERT INTO policy.policy_audit_entries
    (correlation_id, requester_org_id, target_org_id, operation, exchange_purpose,
     decision, policy_version, explanation_json, evaluated_at)
VALUES
    ('seed-corr-0001', 'ORG-QHIN-001', 'ORG-QHIN-002', 'PATIENT_DISCOVERY', 'TREATMENT',  'ALLOW',  'v1', '{"rules":["HIPAA-PR-TPO-PERMITTED","HIPAA-SR-TRANSMISSION-SECURITY","HIPAA-SR-AUDIT-CONTROLS"],"obligations":["MINIMUM_NECESSARY","END_TO_END_ENCRYPTION","AUDIT_TRAIL_REQUIRED"],"regulatory":"45CFR164.506(c)"}', now() - interval '6 hours'),
    ('seed-corr-0002', 'ORG-QHIN-001', 'ORG-PUB-001',  'MESSAGE_DELIVERY', 'PUBLIC_HEALTH','ALLOW', 'v1', '{"rules":["HIPAA-PR-PUBLIC-HEALTH","HIPAA-SR-INTEGRITY","HIPAA-SR-AUDIT-CONTROLS"],"obligations":["MINIMUM_NECESSARY","DIGITAL_SIGNATURE_REQUIRED","AUDIT_TRAIL_REQUIRED"],"regulatory":"45CFR164.512(b)"}', now() - interval '5 hours'),
    ('seed-corr-0003', 'ORG-QHIN-001', 'ORG-SUB-001',  'PATIENT_DISCOVERY', 'MARKETING',  'DENY',   'v1', '{"rules":["HIPAA-PR-MARKETING"],"reason":"Marketing disclosure requires written authorization (45 CFR 164.508(a)(3))."}', now() - interval '4 hours'),
    ('seed-corr-0004', 'ORG-QHIN-002', 'ORG-PUB-001',  'PATIENT_DISCOVERY', 'OPERATIONS', 'DENY',   'v1', '{"rules":["DEFAULT-DENY"],"reason":"Public-health partner not entitled to receive HEALTHCARE_OPERATIONS purpose; fail-closed default applied."}', now() - interval '3 hours'),
    ('seed-corr-0005', 'ORG-SUB-001',  'ORG-QHIN-003', 'DOCUMENT_QUERY',    'TREATMENT',  'ALLOW',  'v1', '{"rules":["HIPAA-PR-TPO-PERMITTED","HIPAA-SR-AUDIT-CONTROLS"],"obligations":["MINIMUM_NECESSARY","AUDIT_TRAIL_REQUIRED"],"regulatory":"45CFR164.506(c)"}', now() - interval '2 hours'),
    ('seed-corr-0006', 'ORG-SUB-002',  'ORG-QHIN-001', 'DOCUMENT_RETRIEVE', 'TREATMENT',  'ALLOW',  'v1', '{"rules":["HIPAA-PR-TPO-PERMITTED","HIPAA-SR-INTEGRITY","HIPAA-SR-AUDIT-CONTROLS"],"obligations":["DIGITAL_SIGNATURE_REQUIRED","AUDIT_TRAIL_REQUIRED"],"regulatory":"45CFR164.506(c)"}', now() - interval '90 minutes')
ON CONFLICT DO NOTHING;

-- ─── Partner registry (drives the ingress.partners admin tables) ────────────
INSERT INTO ingress.partners (partner_id, org_id, name, status, environment, metadata)
VALUES
    ('PT-CW',    'ORG-QHIN-001', 'CommonWell Health Alliance', 'ACTIVE',   'PRODUCTION', '{"contact":"noc@commonwell.test","baaOnFile":true}'::jsonb),
    ('PT-EHX',   'ORG-QHIN-002', 'eHealth Exchange',           'ACTIVE',   'PRODUCTION', '{"contact":"noc@ehx.test","baaOnFile":true}'::jsonb),
    ('PT-EPIC',  'ORG-QHIN-003', 'Epic Nexus QHIN',            'ACTIVE',   'PRODUCTION', '{"contact":"noc@epic.test","baaOnFile":true}'::jsonb),
    ('PT-HOSP',  'ORG-SUB-001',  'Sample Hospital System',     'ACTIVE',   'PRODUCTION', '{"contact":"it@samplehospital.test","baaOnFile":true}'::jsonb),
    ('PT-NORTH', 'ORG-SUB-002',  'Northside Clinic Network',   'INACTIVE', 'STAGING',    '{"contact":"it@northside.test","baaOnFile":true,"note":"Test inactive partner — TEFCA-CA-PARTNER-ACTIVE blocks routing here."}'::jsonb),
    ('PT-COAST', 'ORG-SUB-003',  'Coastal Pediatrics',         'ACTIVE',   'PRODUCTION', '{"contact":"it@coastalpeds.test","baaOnFile":true}'::jsonb),
    ('PT-PHIE',  'ORG-PUB-001',  'StateLab Public Health',     'ACTIVE',   'PRODUCTION', '{"contact":"reporting@statelab.gov.test","baaOnFile":true}'::jsonb)
ON CONFLICT (partner_id) DO NOTHING;

INSERT INTO ingress.partner_certificates
    (certificate_id, partner_id, thumbprint, subject_dn, issuer_dn, serial_number, not_before, not_after, active)
VALUES
    ('CERT-CW-001',    'PT-CW',    'SHA256:11AA22BB33CC44DD55EE66FF77001122334455667788AABBCCDDEEFF0011', 'CN=gateway.commonwell.test, O=CommonWell',  'CN=TEFCA Test Root CA', '0A01', now() - interval '30 days', now() + interval '335 days', true),
    ('CERT-EHX-001',   'PT-EHX',   'SHA256:22BB33CC44DD55EE66FF77881100AABBCCDDEEFF00112233445566778899', 'CN=gateway.ehx.test, O=eHealth Exchange',   'CN=TEFCA Test Root CA', '0A02', now() - interval '20 days', now() + interval '345 days', true),
    ('CERT-EPIC-001',  'PT-EPIC',  'SHA256:33CC44DD55EE66FF778899AA0011223344556677889900AABBCCDDEEFF112233', 'CN=gateway.epic.test, O=Epic Nexus QHIN', 'CN=TEFCA Test Root CA', '0A03', now() - interval '10 days', now() + interval '355 days', true),
    ('CERT-HOSP-001',  'PT-HOSP',  'SHA256:44DD55EE66FF778899AABBCC112233445566778899AABBCCDDEEFF00112233', 'CN=gateway.samplehospital.test',           'CN=TEFCA Test Root CA', '0A04', now() - interval '40 days', now() + interval '325 days', true),
    ('CERT-COAST-001', 'PT-COAST', 'SHA256:55EE66FF778899AABBCCDDEE2233445566778899AABBCCDDEEFF0011223344', 'CN=gateway.coastalpeds.test',              'CN=TEFCA Test Root CA', '0A05', now() - interval '5 days',  now() + interval '360 days', true),
    ('CERT-PHIE-001',  'PT-PHIE',  'SHA256:66FF778899AABBCCDDEEFF33445566778899AABBCCDDEEFF001122334455',   'CN=reporting.statelab.gov.test',          'CN=TEFCA Test Root CA', '0A06', now() - interval '15 days', now() + interval '350 days', true)
ON CONFLICT (thumbprint) DO NOTHING;

INSERT INTO ingress.partner_oauth_config
    (config_id, partner_id, client_id, allowed_scopes, token_ttl_sec, active)
VALUES
    ('OA-CW',    'PT-CW',    'cw-client',    ARRAY['tefca:read','tefca:write'], 3600, true),
    ('OA-EHX',   'PT-EHX',   'ehx-client',   ARRAY['tefca:read','tefca:write'], 3600, true),
    ('OA-EPIC',  'PT-EPIC',  'epic-client',  ARRAY['tefca:read','tefca:write'], 3600, true),
    ('OA-HOSP',  'PT-HOSP',  'hosp-client',  ARRAY['tefca:read','tefca:write'], 1800, true),
    ('OA-COAST', 'PT-COAST', 'coast-client', ARRAY['tefca:read'],               1800, true),
    ('OA-PHIE',  'PT-PHIE',  'phie-client',  ARRAY['tefca:write'],              3600, true)
ON CONFLICT (config_id) DO NOTHING;

INSERT INTO ingress.partner_rate_limits
    (rate_limit_id, partner_id, requests_per_minute, burst_capacity, active)
VALUES
    ('RL-CW',    'PT-CW',    300, 450, true),
    ('RL-EHX',   'PT-EHX',   300, 450, true),
    ('RL-EPIC',  'PT-EPIC',  500, 750, true),
    ('RL-HOSP',  'PT-HOSP',  100, 150, true),
    ('RL-COAST', 'PT-COAST',  50,  75, true),
    ('RL-PHIE',  'PT-PHIE',  200, 300, true)
ON CONFLICT (rate_limit_id) DO NOTHING;

-- ─── Synthetic transactions & audit history (drives Transactions / Audit) ──
INSERT INTO routing.transaction_log
    (correlation_id, idempotency_key, operation, modality, requester_org_id, target_org_id,
     resolved_endpoint, http_status, routing_duration_ms, forward_duration_ms, status, created_at, completed_at)
VALUES
    ('seed-tx-0001', 'idem-seed-0001', 'PATIENT_DISCOVERY', 'XCPD',         'ORG-QHIN-001', 'ORG-QHIN-002', 'http://wiremock:8080/xcpd',         200, 14, 38,  'COMPLETED', now() - interval '6 hours',  now() - interval '6 hours' + interval '60 ms'),
    ('seed-tx-0002', 'idem-seed-0002', 'DOCUMENT_QUERY',    'XCA_QUERY',    'ORG-QHIN-001', 'ORG-QHIN-003', 'http://wiremock:8080/xca/query',    200, 11, 42,  'COMPLETED', now() - interval '5 hours',  now() - interval '5 hours' + interval '70 ms'),
    ('seed-tx-0003', 'idem-seed-0003', 'DOCUMENT_RETRIEVE', 'XCA_RETRIEVE', 'ORG-QHIN-001', 'ORG-QHIN-003', 'http://wiremock:8080/xca/retrieve', 200,  9, 55,  'COMPLETED', now() - interval '4 hours',  now() - interval '4 hours' + interval '80 ms'),
    ('seed-tx-0004', 'idem-seed-0004', 'PATIENT_DISCOVERY', 'XCPD',         'ORG-QHIN-002', 'ORG-PUB-001',  NULL,                                403, 12, NULL, 'DENIED',    now() - interval '3 hours',  now() - interval '3 hours' + interval '20 ms'),
    ('seed-tx-0005', 'idem-seed-0005', 'MESSAGE_DELIVERY',  'XDR',          'ORG-QHIN-001', 'ORG-SUB-001',  'http://wiremock:8080/xdr',          200, 13, 44,  'COMPLETED', now() - interval '2 hours',  now() - interval '2 hours' + interval '70 ms'),
    ('seed-tx-0006', 'idem-seed-0006', 'PATIENT_DISCOVERY', 'XCPD',         'ORG-SUB-002',  'ORG-QHIN-001', 'http://wiremock:8080/xcpd',         200, 18, 31,  'COMPLETED', now() - interval '90 minutes', now() - interval '90 minutes' + interval '60 ms'),
    ('seed-tx-0007', 'idem-seed-0007', 'DOCUMENT_QUERY',    'XCA_QUERY',    'ORG-SUB-002',  'ORG-QHIN-001', 'http://wiremock:8080/xca/query',    200, 16, 39,  'COMPLETED', now() - interval '60 minutes', now() - interval '60 minutes' + interval '70 ms'),
    ('seed-tx-0008', 'idem-seed-0008', 'PATIENT_DISCOVERY', 'XCPD',         'ORG-QHIN-001', 'ORG-SUB-002',  NULL,                                503, 11, NULL, 'FAILED',    now() - interval '40 minutes', now() - interval '40 minutes' + interval '12 ms')
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO audit.audit_events
    (event_id, correlation_id, event_type, operation, requester_org_id, target_org_id, outcome, metadata, patient_id_hash, created_at)
VALUES
    ('evt-seed-0001', 'seed-tx-0001', 'EXCHANGE',         'PATIENT_DISCOVERY','ORG-QHIN-001','ORG-QHIN-002','SUCCESS', '{"matchCount":1}'::jsonb, 'sha256:00aa11', now() - interval '6 hours'),
    ('evt-seed-0002', 'seed-tx-0002', 'EXCHANGE',         'DOCUMENT_QUERY',   'ORG-QHIN-001','ORG-QHIN-003','SUCCESS', '{"docCount":2}'::jsonb,   'sha256:00aa11', now() - interval '5 hours'),
    ('evt-seed-0003', 'seed-tx-0003', 'EXCHANGE',         'DOCUMENT_RETRIEVE','ORG-QHIN-001','ORG-QHIN-003','SUCCESS', '{"bytes":4096}'::jsonb,   'sha256:00aa11', now() - interval '4 hours'),
    ('evt-seed-0004', 'seed-tx-0004', 'POLICY_DENY',      'PATIENT_DISCOVERY','ORG-QHIN-002','ORG-PUB-001', 'DENIED',  '{"rule":"DEFAULT-DENY","regulatory":"fail_closed_default"}'::jsonb, NULL,         now() - interval '3 hours'),
    ('evt-seed-0005', 'seed-tx-0005', 'EXCHANGE',         'MESSAGE_DELIVERY', 'ORG-QHIN-001','ORG-SUB-001', 'SUCCESS', '{"messageId":"msg-mock-001"}'::jsonb, NULL, now() - interval '2 hours'),
    ('evt-seed-0006', 'seed-tx-0008', 'PARTNER_FAILURE',  'PATIENT_DISCOVERY','ORG-QHIN-001','ORG-SUB-002', 'ERROR', '{"reason":"upstream 503"}'::jsonb, NULL, now() - interval '40 minutes')
ON CONFLICT (event_id) DO NOTHING;
