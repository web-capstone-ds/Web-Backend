-- Cpk(공정능력지수)는 dimension_w_mm(패키지 폭) 기준으로 계산한다.
-- 기존 recipe_specs USL/LSL(12.04/11.96)은 실제 측정값(~10mm)과 스케일이 맞지 않아
-- 잘못 계산하면 음수 Cpk 등 이상값이 나온다. 실측 공칭 10.00mm ± 0.04 스케일로 정정한다.
-- (실제 공정 규격이 확정되면 후속 마이그레이션으로 재정정한다.)

UPDATE recipe_specs
SET target_value = 10.00,
    usl          = 10.04,
    lsl          = 9.96,
    updated_at   = NOW()
WHERE recipe_id IN ('Carsem_3X3', 'Carsem_4X6');

-- 신규 레시피 대비 기본값(없으면 무시)
INSERT INTO recipe_specs (recipe_id, target_value, usl, lsl, lcl_yield, ideal_cycle_ms) VALUES
    ('Carsem_3X3', 10.00, 10.04, 9.96, 95.0, 1620),
    ('Carsem_4X6', 10.00, 10.04, 9.96, 65.0, 1620)
ON CONFLICT (recipe_id) DO UPDATE
    SET target_value = EXCLUDED.target_value,
        usl          = EXCLUDED.usl,
        lsl          = EXCLUDED.lsl,
        updated_at   = NOW();
