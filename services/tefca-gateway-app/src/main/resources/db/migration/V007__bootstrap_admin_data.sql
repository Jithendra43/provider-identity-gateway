-- ───────────────────────────────────────────────────────────────────────────
-- V007__bootstrap_admin_data.sql
-- ───────────────────────────────────────────────────────────────────────────
-- One-time bootstrap that guarantees the minimum directory + policy data
-- needed for the prod admin console to function:
--
--   1. The "ORG-ADMIN-CONSOLE" requester org used by the Test Console when
--      an admin is signed in via Cognito SSO (no JWT orgId claim is issued
--      so IngressOrchestrator falls back to tefca.admin.test-harness.org-id;
--      RequesterOrgValidator must find this org active in the directory).
--   2. The headline TPO-permitted policy rule + the fail-closed default
--      rule, in case the R003 repeatable migration was previously skipped
--      (Repeatable migrations are silently re-run on every checksum change
--      but a fresh database that has never seen R003 will only get them on
--      the next deploy — V007 is a versioned guarantee).
--   3. The other QHIN/SUB seed rows (so the Directory page and BAA
--      validator have data) — copied from R001 with ON CONFLICT DO NOTHING
--      so they remain idempotent regardless of whether R001 ran.
--
-- All inserts use ON CONFLICT … DO NOTHING; running this against an already
-- seeded database is a no-op.
-- ───────────────────────────────────────────────────────────────────────────

-- ─── Directory: orgs ───────────────────────────────────────────────────────
INSERT INTO directory.directory_organizations (org_id, name, oid, org_type, active, home_community_id)
VALUES
    ('ORG-ADMIN-CONSOLE', 'TEFCA Gateway Admin Console',  '2.16.840.1.113883.3.7777',  'QHIN', true, 'urn:oid:2.16.840.1.113883.3.7777'),
    ('ORG-QHIN-001',      'CommonWell Health Alliance',   '2.16.840.1.113883.3.6147',  'QHIN', true, 'urn:oid:2.16.840.1.113883.3.6147'),
    ('ORG-QHIN-002',      'eHealth Exchange',             '2.16.840.1.113883.3.6037',  'QHIN', true, 'urn:oid:2.16.840.1.113883.3.6037'),
    ('ORG-SUB-001',       'Sample Hospital System',       '2.16.840.1.113883.3.9999',  'SUB_PARTICIPANT', true, 'urn:oid:2.16.840.1.113883.3.9999')
ON CONFLICT (org_id) DO NOTHING;

-- ─── Directory: nodes ──────────────────────────────────────────────────────
INSERT INTO directory.directory_nodes (node_id, org_id, name, home_community_id, status)
VALUES
    ('NODE-ADMIN-CONSOLE', 'ORG-ADMIN-CONSOLE', 'Admin Console Operator Node', 'urn:oid:2.16.840.1.113883.3.7777.1', 'ACTIVE'),
    ('NODE-CW-001',        'ORG-QHIN-001',      'CommonWell Primary Node',     'urn:oid:2.16.840.1.113883.3.6147.1', 'ACTIVE'),
    ('NODE-EHX-001',       'ORG-QHIN-002',      'eHealth Exchange Primary Node','urn:oid:2.16.840.1.113883.3.6037.1', 'ACTIVE'),
    ('NODE-HOSP-001',      'ORG-SUB-001',       'Sample Hospital Node',        'urn:oid:2.16.840.1.113883.3.9999.1', 'ACTIVE')
ON CONFLICT (node_id) DO NOTHING;

-- ─── Policy rules: minimum viable ruleset ──────────────────────────────────
-- R003 contains the full ~20-rule HIPAA/TEFCA ruleset. These two are the
-- only ones the Test Console depends on to return PERMITTED for the canonical
-- TREATMENT/PATIENT_DISCOVERY sample request. Inserts are idempotent so the
-- full ruleset from R003 is preserved when present.
INSERT INTO policy.policy_rules
    (rule_id, rule_name, category, description, rule_expression, priority, active)
VALUES
    ('HIPAA-PR-TPO-PERMITTED',
        'Treatment, Payment, Healthcare Operations are permitted',
        'HIPAA_PRIVACY_RULE',
        '45 CFR 164.506(c): A covered entity may use or disclose PHI for its own treatment, payment, and healthcare operations without authorization. Permit and attach minimum-necessary + audit obligations.',
        'request.exchangePurpose IN ("TREATMENT","PAYMENT","HEALTHCARE_OPERATIONS") -> PERMIT + obligations:[MINIMUM_NECESSARY, AUDIT_TRAIL_REQUIRED] (regulatory:45CFR164.506(c))',
        100, true),
    ('DEFAULT-DENY',
        'Default deny (fail-closed)',
        'DEFAULT',
        'TEFCA & HIPAA fail-closed posture: any request not matched by an explicit PERMIT rule is denied. This is the residual rule and runs last.',
        'true -> DENY (regulatory:fail_closed_default)',
        9999, true)
ON CONFLICT (rule_id) DO NOTHING;
