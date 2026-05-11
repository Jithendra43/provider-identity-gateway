-- ───────────────────────────────────────────────────────────────────────────
-- V011__pa_seed.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Seeds the C-HIT Prior Authorization (PA) virtual organization, three
-- service nodes (CRD / DTR / PAS), and 14 endpoint rows that map each
-- modality enum value to a downstream URL.
--
-- Activation matrix (per the integration handoff doc):
--   • CRD nodes — ACTIVE (live)
--   • DTR nodes — INACTIVE (pending downstream readiness)
--   • PAS nodes — INACTIVE (pending downstream readiness)
--
-- All URLs point at the loopback MockPaController until the downstream
-- services are ready. Operators flip an endpoint to active=false and
-- update the URL via the admin Directory page when the real service is
-- ready, with no redeploy required.
--
-- Also seeds a new HIPAA policy rule (priority 110) that PERMITs requests
-- whose exchange purpose is PRIOR_AUTHORIZATION and attaches the standard
-- minimum-necessary + audit obligations.
-- ───────────────────────────────────────────────────────────────────────────

-- ─── Org ──────────────────────────────────────────────────────────────────
INSERT INTO directory.directory_organizations
        (org_id,                 name,                                  oid,                              org_type,          active, home_community_id)
VALUES  ('ORG-CHIT-PA-PLATFORM', 'C-HIT Prior Authorization Platform',  '2.16.840.1.113883.3.7777.100',   'SUB_PARTICIPANT', TRUE,   'urn:oid:2.16.840.1.113883.3.7777.100')
ON CONFLICT (org_id) DO NOTHING;

-- ─── Nodes ────────────────────────────────────────────────────────────────
INSERT INTO directory.directory_nodes
        (node_id,        org_id,                 name,                              home_community_id,                       status)
VALUES  ('NODE-CRD-001', 'ORG-CHIT-PA-PLATFORM', 'PA Coverage Requirements (CRD)',  'urn:oid:2.16.840.1.113883.3.7777.100.1', 'ACTIVE'),
        ('NODE-DTR-001', 'ORG-CHIT-PA-PLATFORM', 'PA Documentation Templates (DTR)','urn:oid:2.16.840.1.113883.3.7777.100.2', 'ACTIVE'),
        ('NODE-PAS-001', 'ORG-CHIT-PA-PLATFORM', 'PA Support Claims (PAS)',         'urn:oid:2.16.840.1.113883.3.7777.100.3', 'ACTIVE')
ON CONFLICT (node_id) DO NOTHING;

-- ─── CRD endpoints (CDS Hooks 2.0 — POST) ────────────────────────────────
INSERT INTO directory.directory_endpoints
        (endpoint_id,                node_id,        url,                                                            modality,                       active, timeout_ms, health_check_url)
VALUES  ('EP-CRD-ORDER-SIGN',        'NODE-CRD-001', 'http://127.0.0.1:8080/mock-pa/cds-services/pa-order-sign',     'PA_ORDER_SIGN',                TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services'),
        ('EP-CRD-ORDER-SELECT',      'NODE-CRD-001', 'http://127.0.0.1:8080/mock-pa/cds-services/pa-order-select',   'PA_ORDER_SELECT',              TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services'),
        ('EP-CRD-APPOINTMENT-BOOK',  'NODE-CRD-001', 'http://127.0.0.1:8080/mock-pa/cds-services/pa-appointment-book','PA_APPOINTMENT_BOOK',         TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services'),
        ('EP-CRD-ORDER-DISPATCH',    'NODE-CRD-001', 'http://127.0.0.1:8080/mock-pa/cds-services/pa-order-dispatch', 'PA_ORDER_DISPATCH',            TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services'),
        ('EP-CRD-ENCOUNTER-START',   'NODE-CRD-001', 'http://127.0.0.1:8080/mock-pa/cds-services/pa-encounter-start','PA_ENCOUNTER_START',           TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services'),
        ('EP-CRD-ENCOUNTER-DISCHARGE','NODE-CRD-001','http://127.0.0.1:8080/mock-pa/cds-services/pa-encounter-discharge','PA_ENCOUNTER_DISCHARGE',   TRUE,   5000,       'http://127.0.0.1:8080/mock-pa/cds-services')
ON CONFLICT (endpoint_id) DO NOTHING;

-- ─── DTR endpoints (FHIR REST) — INACTIVE until downstream ready ─────────
INSERT INTO directory.directory_endpoints
        (endpoint_id,                node_id,        url,                                                                       modality,                       active, timeout_ms, health_check_url)
VALUES  ('EP-DTR-Q-PACKAGE',         'NODE-DTR-001', 'http://127.0.0.1:8080/mock-pa/fhir/Questionnaire/$questionnaire-package', 'PA_DTR_QUESTIONNAIRE_PACKAGE', FALSE,  5000,       NULL),
        ('EP-DTR-Q-READ',            'NODE-DTR-001', 'http://127.0.0.1:8080/mock-pa/fhir/Questionnaire',                        'PA_DTR_QUESTIONNAIRE_READ',    FALSE,  5000,       NULL),
        ('EP-DTR-LIBRARY-READ',      'NODE-DTR-001', 'http://127.0.0.1:8080/mock-pa/fhir/Library',                              'PA_DTR_LIBRARY_READ',          FALSE,  5000,       NULL),
        ('EP-DTR-RESPONSE-SUBMIT',   'NODE-DTR-001', 'http://127.0.0.1:8080/mock-pa/fhir/QuestionnaireResponse',                'PA_DTR_RESPONSE_SUBMIT',       FALSE,  5000,       NULL),
        ('EP-DTR-RESPONSE-READ',     'NODE-DTR-001', 'http://127.0.0.1:8080/mock-pa/fhir/QuestionnaireResponse',                'PA_DTR_RESPONSE_READ',         FALSE,  5000,       NULL)
ON CONFLICT (endpoint_id) DO NOTHING;

-- ─── PAS endpoints (FHIR REST X12-bridged) — INACTIVE until downstream ready ─
INSERT INTO directory.directory_endpoints
        (endpoint_id,                node_id,        url,                                                                modality,                  active, timeout_ms, health_check_url)
VALUES  ('EP-PAS-CLAIM-SUBMIT',      'NODE-PAS-001', 'http://127.0.0.1:8080/mock-pa/fhir/Claim/$submit',                 'PA_CLAIM_SUBMIT',         FALSE,  10000,      NULL),
        ('EP-PAS-CLAIM-INQUIRE',     'NODE-PAS-001', 'http://127.0.0.1:8080/mock-pa/fhir/Claim/$inquire',                'PA_CLAIM_INQUIRE',        FALSE,  10000,      NULL),
        ('EP-PAS-CLAIM-RESPONSE-READ','NODE-PAS-001','http://127.0.0.1:8080/mock-pa/fhir/ClaimResponse',                  'PA_CLAIM_RESPONSE_READ',  FALSE,  10000,      NULL)
ON CONFLICT (endpoint_id) DO NOTHING;

-- ─── Policy rule: PRIOR_AUTHORIZATION → PERMIT + obligations ─────────────
INSERT INTO policy.policy_rules
    (rule_id, rule_name, category, description, rule_expression, priority, active)
VALUES
    ('HIPAA-PR-PA-PERMITTED',
        'Prior Authorization is permitted (TPO derivative)',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.506(c)(1) and 164.502(b): PA workflows are payment-purpose disclosures permitted without authorization. Permit and attach minimum-necessary + audit obligations.',
        'request.exchangePurpose IN ("PRIOR_AUTHORIZATION") -> PERMIT + obligations:[MINIMUM_NECESSARY, AUDIT_TRAIL_REQUIRED] (regulatory:45CFR164.506(c)+164.502(b))',
        110, TRUE)
ON CONFLICT (rule_id) DO NOTHING;
