ALTER TABLE analysis_reports
    ALTER COLUMN content TYPE JSONB USING content::jsonb;
