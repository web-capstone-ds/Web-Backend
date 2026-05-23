ALTER TABLE query_history
    ALTER COLUMN filters TYPE JSONB USING CASE
        WHEN filters IS NULL OR btrim(filters) = '' THEN NULL
        ELSE filters::jsonb
    END,
    ALTER COLUMN sources TYPE JSONB USING CASE
        WHEN sources IS NULL OR btrim(sources) = '' THEN NULL
        ELSE sources::jsonb
    END;

ALTER TABLE audit_logs
    ALTER COLUMN details TYPE JSONB USING CASE
        WHEN details IS NULL OR btrim(details) = '' THEN NULL
        ELSE details::jsonb
    END;
