-- No-op migration.
-- V8 now owns the operator_id users schema, users_version_bump trigger,
-- and refresh_tokens table for clean test database bootstrapping.
SELECT 1;
