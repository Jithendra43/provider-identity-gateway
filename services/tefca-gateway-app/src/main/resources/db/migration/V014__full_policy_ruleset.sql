-- V014: Authoritative HIPAA / 42 CFR Part 2 / TEFCA / Operational policy ruleset.
--
-- V007 only bootstraps the 2 rules the smoke test depends on
-- (HIPAA-PR-TPO + DEFAULT-DENY). R003 is a test-dataset *repeatable*
-- whose directory inserts can collide with the PA schema added by V010,
-- preventing the policy rows from ever landing on prod. This versioned
-- migration inserts the full HIPAA/Part2/TEFCA ruleset in isolation so the
-- admin UI shows every regulatory rule on every environment.
--
-- All inserts are idempotent (ON CONFLICT DO NOTHING) so they are safe to
-- re-apply on environments where R003 already populated the table.

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
SELECT rule_id, 1, rule_expression, 'system-seed', 'Initial HIPAA-aligned ruleset (V014)'
FROM policy.policy_rules
WHERE rule_id IN (
        'HIPAA-PR-MARKETING',
        'HIPAA-PR-SALE-OF-PHI',
        'HIPAA-PR-PSYCHOTHERAPY-NOTES',
        'CFR-PART2-SUD',
        'HIPAA-PR-TPO-PERMITTED',
        'HIPAA-PR-INDIVIDUAL-ACCESS',
        'HIPAA-PR-PUBLIC-HEALTH',
        'HIPAA-PR-REQUIRED-BY-LAW',
        'TEFCA-BREAKGLASS',
        'HIPAA-PR-MINIMUM-NECESSARY',
        'HIPAA-SR-TRANSMISSION-SECURITY',
        'HIPAA-SR-INTEGRITY',
        'HIPAA-SR-ACCESS-CONTROL',
        'HIPAA-SR-AUDIT-CONTROLS',
        'TEFCA-CA-PARTNER-ACTIVE',
        'TEFCA-CA-BAA-REQUIRED',
        'PHI-SSN-ENCRYPTION',
        'PHI-SENSITIVE-DATA-CLASSES',
        'OP-RATE-LIMIT',
        'DEFAULT-DENY'
    )
ON CONFLICT (rule_id, version_number) DO NOTHING;
