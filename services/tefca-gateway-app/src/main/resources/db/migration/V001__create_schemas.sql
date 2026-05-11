-- V001: Create schemas
-- Each service gets its own schema for clean separation

CREATE SCHEMA IF NOT EXISTS policy;
CREATE SCHEMA IF NOT EXISTS directory;
CREATE SCHEMA IF NOT EXISTS routing;
CREATE SCHEMA IF NOT EXISTS audit;
