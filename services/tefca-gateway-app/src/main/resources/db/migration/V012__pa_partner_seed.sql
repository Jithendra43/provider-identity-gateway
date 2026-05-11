-- ───────────────────────────────────────────────────────────────────────────
-- V012__pa_partner_seed.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Seeds the inbound partner identity used by the production PA smoke test.
--
-- The repeatable migrations R003__test_dataset.sql and R004__partner_certificates.sql
-- never run because they violate Flyway's repeatable-migration naming
-- convention (`R__name.sql`, no version digits). To make the
-- /api/v1/pa/** flow demonstrable end-to-end we explicitly seed the Epic
-- QHIN partner record + its mTLS certificate thumbprint here as a
-- versioned migration.
--
-- The seeded thumbprint matches test-harness/certs/PART-EPIC-001.crt
-- (sha256 = 5573:26:E5:0F:34:CB:77:A5:A0:9E:F1:26:DF:BD:8B:65:9F:34:6F:66:69:87:D6:5A:C2:C9:E4:2B:E6:DB:93).
-- Operators can deactivate this row from the admin UI at any time without
-- a redeploy.
-- ───────────────────────────────────────────────────────────────────────────

-- Provider organization referenced by the partner row. Idempotent — safe
-- if R003 ever does run later.
INSERT INTO directory.directory_organizations
        (org_id,        name,                oid,                              org_type, active, home_community_id)
VALUES
        ('ORG-QHIN-003','Epic Nexus QHIN',   '2.16.840.1.113883.3.7204',       'QHIN',   true,   'urn:oid:2.16.840.1.113883.3.7204')
ON CONFLICT (org_id) DO NOTHING;

-- Inbound partner record.
INSERT INTO ingress.partners
        (partner_id, org_id,        name,                 status,   environment,  metadata)
VALUES
        ('PT-EPIC',  'ORG-QHIN-003','Epic Nexus QHIN',    'ACTIVE', 'PRODUCTION', '{"contact":"noc@epic.test","baaOnFile":true}'::jsonb)
ON CONFLICT (partner_id) DO UPDATE
        SET status      = EXCLUDED.status,
            org_id      = EXCLUDED.org_id,
            name        = EXCLUDED.name,
            environment = EXCLUDED.environment,
            metadata    = EXCLUDED.metadata,
            updated_at  = now();

-- mTLS certificate thumbprint → partner mapping.
INSERT INTO ingress.partner_certificates
        (certificate_id,                partner_id, thumbprint,                                                          subject_dn,                                                                                              issuer_dn,                                                                                          serial_number,                              not_before,                  not_after,                   active)
VALUES
        ('CERT-EPIC-PA-SMOKE-001',      'PT-EPIC',  '557326e50f34cb77a5a09ef126dfbd8b659f346f666987d65ac2c9e42be6db93',  'C=US, ST=DC, O=Epic Nexus QHIN, OU=TEFCA QHIN Partner, CN=ORG-QHIN-003.qhin.epic.local',                'C=US, ST=DC, O=C-HIT TEFCA Test CA, OU=Local Development Only, CN=TEFCA Local Root CA',           '432487FEFA7D9C98B86509626F12AC1BD98EA715', '2026-04-28 22:47:34+00',    '2028-07-31 22:47:34+00',    TRUE)
ON CONFLICT (thumbprint) DO UPDATE
        SET active     = EXCLUDED.active,
            partner_id = EXCLUDED.partner_id,
            not_after  = EXCLUDED.not_after;
