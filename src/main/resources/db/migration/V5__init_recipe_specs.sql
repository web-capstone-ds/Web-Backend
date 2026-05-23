CREATE TABLE recipe_specs (
    recipe_id       TEXT            PRIMARY KEY,
    target_value    DOUBLE PRECISION,
    usl             DOUBLE PRECISION,
    lsl             DOUBLE PRECISION,
    lcl_yield       DOUBLE PRECISION DEFAULT 95.0,
    ideal_cycle_ms  INTEGER         DEFAULT 1620,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO recipe_specs (recipe_id, usl, lsl, lcl_yield, ideal_cycle_ms) VALUES
    ('Carsem_3X3', 12.04, 11.96, 95.0, 1620),
    ('Carsem_4X6', 12.04, 11.96, 65.0, 1620)
ON CONFLICT (recipe_id) DO NOTHING;
