-- V007: Backfill DirectoryEndpoint optional columns that exist in the JPA
-- entity (timeout_ms, health_check_url) but were omitted from V002. Idempotent
-- so it is safe to apply on existing prod RDS where Hibernate auto-DDL is off.

ALTER TABLE directory.directory_endpoints
    ADD COLUMN IF NOT EXISTS timeout_ms       INTEGER,
    ADD COLUMN IF NOT EXISTS health_check_url VARCHAR(512);
