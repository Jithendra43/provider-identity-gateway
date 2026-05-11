-- V016: Seed representative audit events so the admin Audit Trail page is
-- meaningfully populated on a fresh production deploy.
--
-- Background: the Audit Trail UI reads from audit.audit_events. R003 (the
-- repeatable test-dataset migration) is documented in V012 as never running
-- in any environment because its checksum-style naming violates Flyway's
-- repeatable rules, so production starts with an empty audit table and the
-- page renders "No events." This versioned migration mirrors R003's audit
-- block (plus a few extra rows covering DENIED + ERROR outcomes and the PA
-- modality) so the page is never empty out-of-the-box.
--
-- Idempotent: ON CONFLICT (event_id) DO NOTHING ensures re-running is safe
-- and any real audit rows produced by traffic are left untouched.

INSERT INTO audit.audit_events
    (event_id, correlation_id, event_type, operation, requester_org_id, target_org_id, outcome, metadata, patient_id_hash, created_at)
VALUES
    ('evt-bootstrap-0001', 'corr-bootstrap-0001', 'EXCHANGE',        'PATIENT_DISCOVERY','ORG-QHIN-001','ORG-QHIN-002','SUCCESS', '{"matchCount":1,"latencyMs":62}'::jsonb,            'sha256:00aa11', now() - interval '6 hours'),
    ('evt-bootstrap-0002', 'corr-bootstrap-0002', 'EXCHANGE',        'DOCUMENT_QUERY',   'ORG-QHIN-001','ORG-QHIN-003','SUCCESS', '{"docCount":2,"latencyMs":71}'::jsonb,              'sha256:00aa11', now() - interval '5 hours'),
    ('evt-bootstrap-0003', 'corr-bootstrap-0003', 'EXCHANGE',        'DOCUMENT_RETRIEVE','ORG-QHIN-001','ORG-QHIN-003','SUCCESS', '{"bytes":4096,"latencyMs":83}'::jsonb,              'sha256:00aa11', now() - interval '4 hours'),
    ('evt-bootstrap-0004', 'corr-bootstrap-0004', 'POLICY_DENY',     'PATIENT_DISCOVERY','ORG-QHIN-002','ORG-PUB-001', 'DENIED',  '{"rule":"DEFAULT-DENY","regulatory":"fail_closed_default"}'::jsonb, NULL,            now() - interval '3 hours'),
    ('evt-bootstrap-0005', 'corr-bootstrap-0005', 'EXCHANGE',        'MESSAGE_DELIVERY', 'ORG-QHIN-001','ORG-SUB-001', 'SUCCESS', '{"messageId":"msg-mock-001"}'::jsonb,               NULL,            now() - interval '2 hours'),
    ('evt-bootstrap-0006', 'corr-bootstrap-0006', 'PARTNER_FAILURE', 'PATIENT_DISCOVERY','ORG-QHIN-001','ORG-SUB-002', 'ERROR',   '{"reason":"upstream 503"}'::jsonb,                  NULL,            now() - interval '40 minutes'),
    ('evt-bootstrap-0007', 'corr-bootstrap-0007', 'AUTH_SUCCESS',    'ADMIN_LOGIN',      'ORG-QHIN-001', NULL,         'SUCCESS', '{"subject":"test@local.dev","method":"cognito-oidc"}'::jsonb, NULL, now() - interval '30 minutes'),
    ('evt-bootstrap-0008', 'corr-bootstrap-0008', 'EXCHANGE',        'PA_ORDER_SIGN',    'ORG-PROV-EPIC','ORG-PAYER-001','SUCCESS','{"hook":"order-sign","cardCount":1}'::jsonb,        NULL,            now() - interval '20 minutes'),
    ('evt-bootstrap-0009', 'corr-bootstrap-0009', 'EXCHANGE',        'PA_DTR_QUESTIONNAIRE_PACKAGE','ORG-PROV-EPIC','ORG-PAYER-001','SUCCESS','{"questionnaireCount":1,"latencyMs":94}'::jsonb, NULL, now() - interval '15 minutes'),
    ('evt-bootstrap-0010', 'corr-bootstrap-0010', 'EXCHANGE',        'PA_CLAIM_SUBMIT',  'ORG-PROV-EPIC','ORG-PAYER-001','SUCCESS','{"claimId":"clm-001","disposition":"approved"}'::jsonb, NULL,        now() - interval '10 minutes'),
    ('evt-bootstrap-0011', 'corr-bootstrap-0011', 'POLICY_DENY',     'PA_CLAIM_SUBMIT',  'ORG-PROV-CERN','ORG-PAYER-001','DENIED', '{"rule":"PA-PRIOR-AUTH-CONSENT","reason":"missing patient consent"}'::jsonb, NULL, now() - interval '5 minutes'),
    ('evt-bootstrap-0012', 'corr-bootstrap-0012', 'EXCHANGE',        'PATIENT_DISCOVERY','ORG-QHIN-001','ORG-QHIN-002','SUCCESS', '{"matchCount":3,"latencyMs":58}'::jsonb,            'sha256:11bb22', now() - interval '2 minutes')
ON CONFLICT (event_id) DO NOTHING;
