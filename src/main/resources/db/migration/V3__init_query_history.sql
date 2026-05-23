CREATE TABLE query_history (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    question        TEXT            NOT NULL,
    filters         TEXT,
    answer          TEXT,
    sources         TEXT,
    confidence      DOUBLE PRECISION,
    response_time_ms INTEGER,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_query_user ON query_history (user_id, created_at DESC);

CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id),
    action          VARCHAR(50)     NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    details         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);
