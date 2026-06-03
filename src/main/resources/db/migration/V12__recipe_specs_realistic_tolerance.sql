-- recipe_specs USL/LSL 현실화.
-- V11의 ±0.04mm는 기존 12.04/11.96 placeholder에서 폭만 옮긴 값이라 dimension_w_mm(~10mm)
-- 패키지 폭의 실제 공차 대비 지나치게 좁았다. 그 결과 정상 공정도 Cpk가 1.33 미만으로만 나와
-- 대시보드가 사실상 항상 "경고"로 표시됐다.
-- 약 10mm 패키지 바디 폭의 현실적 공차(±0.10mm, 공칭 10.00) 수준으로 정정한다.
-- (실제 도면 공차가 확정되면 후속 마이그레이션으로 재정정.)

UPDATE recipe_specs
SET target_value = 10.00,
    usl          = 10.10,
    lsl          = 9.90,
    updated_at   = NOW()
WHERE recipe_id IN ('Carsem_3X3', 'Carsem_4X6');

INSERT INTO recipe_specs (recipe_id, target_value, usl, lsl, lcl_yield, ideal_cycle_ms) VALUES
    ('Carsem_3X3', 10.00, 10.10, 9.90, 95.0, 1620),
    ('Carsem_4X6', 10.00, 10.10, 9.90, 65.0, 1620)
ON CONFLICT (recipe_id) DO UPDATE
    SET target_value = EXCLUDED.target_value,
        usl          = EXCLUDED.usl,
        lsl          = EXCLUDED.lsl,
        updated_at   = NOW();
