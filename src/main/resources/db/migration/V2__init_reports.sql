CREATE TABLE analysis_reports (
    id              BIGSERIAL       PRIMARY KEY,
    report_id       UUID            NOT NULL UNIQUE,
    report_type     VARCHAR(20)     NOT NULL,
    period_start    TIMESTAMPTZ     NOT NULL,
    period_end      TIMESTAMPTZ     NOT NULL,
    summary         TEXT            NOT NULL,
    content         TEXT            NOT NULL,
    generated_at    TIMESTAMPTZ     NOT NULL,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_type_time ON analysis_reports (report_type, generated_at DESC);
CREATE INDEX idx_report_period ON analysis_reports (period_start, period_end);
