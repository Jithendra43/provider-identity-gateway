-- ───────────────────────────────────────────────────────────────────────────
-- V010__pa_directory_schema.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Adds two columns to directory.directory_endpoints required by the Prior
-- Authorization (PA) routing flow:
--
--   * timeout_ms       — per-endpoint forward timeout. PAS (claim submit/
--                        inquire) tolerates 10s; CRD/DTR target 5s. NULL
--                        falls back to the legacy 25s default.
--   * health_check_url — reserved for the EndpointHealthTracker probe; not
--                        used by V010 itself but populated for PA seeds in
--                        V011 so the active-CRD nodes are eligible for the
--                        future probe loop.
--
-- Idempotent (IF NOT EXISTS) so the migration is safe on environments that
-- already manually patched the schema.
-- ───────────────────────────────────────────────────────────────────────────

ALTER TABLE directory.directory_endpoints
    ADD COLUMN IF NOT EXISTS timeout_ms       INTEGER      NULL,
    ADD COLUMN IF NOT EXISTS health_check_url VARCHAR(512) NULL;
