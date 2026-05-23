CREATE TABLE actions_log (
    action_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    equipment_id    TEXT            NOT NULL,
    alarm_id        TEXT,
    action_status   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    action_type     TEXT            NOT NULL,
    performed_by    TEXT            NOT NULL,
    performed_at    TIMESTAMPTZ     NOT NULL,
    result_before   DOUBLE PRECISION,
    result_after    DOUBLE PRECISION,
    note            TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_actions_equip ON actions_log (equipment_id, performed_at DESC);
CREATE INDEX idx_actions_status ON actions_log (action_status);

CREATE TABLE report_reviews (
    review_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date     DATE            NOT NULL,
    reviewer_role   VARCHAR(20)     NOT NULL,
    reviewer_name   TEXT            NOT NULL,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_date ON report_reviews (report_date);

CREATE TABLE report_comments (
    comment_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date     DATE            NOT NULL,
    author          TEXT            NOT NULL,
    content         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_date ON report_comments (report_date, created_at DESC);
