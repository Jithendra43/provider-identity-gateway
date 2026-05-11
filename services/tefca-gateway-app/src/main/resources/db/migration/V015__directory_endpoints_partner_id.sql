-- ───────────────────────────────────────────────────────────────────────────
-- V015__directory_endpoints_partner_id.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Adds the nullable partner_id column to directory.directory_endpoints so
-- locally-onboarded partners (managed via the ingress partner onboarding API)
-- can be linked back to the directory rows that represent their endpoints.
--
-- The ingress-auth-service DirectoryEndpoint JPA view expects this column to
-- exist (see chit.tefca.ingress.model.DirectoryEndpoint#partnerId). Without
-- it Hibernate schema validation fails at startup.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE directory.directory_endpoints
    ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_directory_endpoints_partner_id
    ON directory.directory_endpoints(partner_id);
