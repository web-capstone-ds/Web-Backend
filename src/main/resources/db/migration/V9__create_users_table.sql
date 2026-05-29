CREATE TABLE IF NOT EXISTS users (
  operator_id   TEXT PRIMARY KEY,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL CHECK (role IN ('OPERATOR','ENGINEER','ADMIN')),
  active        BOOLEAN DEFAULT TRUE,
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  version       BIGINT DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_users_version ON users (version);

CREATE OR REPLACE FUNCTION trg_users_version()
RETURNS TRIGGER AS $$
BEGIN NEW.version := OLD.version + 1; NEW.updated_at := NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_version_bump
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION trg_users_version();
