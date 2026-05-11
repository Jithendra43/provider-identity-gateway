-- V008: Link directory.directory_endpoints to ingress.partners so partner
-- onboarding/offboarding can create / suspend the routing entries that
-- correspond to a partner without losing the existing node-based hierarchy.
--
-- Nullable on purpose: legacy endpoints loaded from the RCE directory snapshot
-- are not associated with a locally-onboarded partner and must keep working.

ALTER TABLE directory.directory_endpoints
    ADD COLUMN IF NOT EXISTS partner_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_directory_endpoints_partner
    ON directory.directory_endpoints(partner_id);
