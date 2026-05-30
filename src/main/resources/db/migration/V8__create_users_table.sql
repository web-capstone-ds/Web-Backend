-- Redefine auth tables with operator_id as the stable user key.
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
  operator_id   TEXT PRIMARY KEY,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL CHECK (role IN ('OPERATOR','ENGINEER','ADMIN')),
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  version       BIGINT NOT NULL DEFAULT 1
);
CREATE INDEX idx_users_version ON users (version);

CREATE OR REPLACE FUNCTION trg_users_version()
RETURNS TRIGGER AS $$
BEGIN NEW.version := OLD.version + 1; NEW.updated_at := NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_version_bump
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION trg_users_version();

CREATE TABLE refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(operator_id),
  token_hash  TEXT NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

ALTER TABLE query_history
  ALTER COLUMN user_id TYPE TEXT USING user_id::TEXT;

ALTER TABLE audit_logs
  ALTER COLUMN user_id TYPE TEXT USING user_id::TEXT;
