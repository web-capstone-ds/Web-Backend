package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.DerivedBatchStats;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.equipment.service.RecipeSpecService;
import com.ds.backend.equipment.service.RecipeSpecService.SpecValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CpkCalculationServiceTest {
    // dimension_w_mm 공칭 10.00 ± 0.04 (V11 recipe_specs와 동일 스케일)
    private final RecipeSpecService recipeSpecService = new RecipeSpecService(null) {
        @Override
        public SpecValues getSpec(String recipeId) {
            return new SpecValues("Carsem_3X3", 10.00, 10.04, 9.96, 95.0);
        }
    };
    private final CpkCalculationService service = new CpkCalculationService(recipeSpecService, "Carsem_3X3");

    @Test
    void returnsNullWhenLatestBatchIsMissing() {
        var result = service.fromLatest(Optional.empty());

        assertThat(result.cpk()).isNull();
        assertThat(result.reliable()).isFalse();
        assertThat(result.sub()).contains("AI 최신 배치 데이터 없음");
    }

    @Test
    void unavailableWhenNoMetricData() {
        var derived = new DerivedBatchStats(List.of(), List.of(), List.of(), Map.of(), List.of());
        var result = service.fromLatest(Optional.of(new BatchDetailResponse(null, derived)));

        assertThat(result.cpk()).isNull();
        assertThat(result.sub()).contains("측정 metric 데이터 없음");
    }

    @Test
    void usesRecipeSpecWhenMetricUslLslAreNull() {
        // AI 서버는 usl/lsl을 항상 null로 보냄 → recipe spec(10.04/9.96)으로 계산
        var metric = new MetricStat("dimension_w_mm", 2792, 10.00, 0.01, 9.97, 10.03, 10.0, 10.02, 10.03,
                null, null, null, null, null);
        var result = service.fromLatest(Optional.of(batchWith(metric)));

        // cpk = min((10.04-10.00)/(3*0.01),(10.00-9.96)/(3*0.01)) = 1.33
        assertThat(result.cpk()).isEqualTo(1.33);
        assertThat(result.reliable()).isTrue();
        assertThat(result.status()).isEqualTo("normal");
    }

    @Test
    void computesButFlagsUnreliableWhenSampleSizeIsTooSmall() {
        var metric = new MetricStat("dimension_w_mm", 29, 10.00, 0.01, 9.99, 10.01, 10.0, 10.01, 10.01,
                null, null, null, null, null);
        var result = service.fromLatest(Optional.of(batchWith(metric)));

        assertThat(result.cpk()).isNotNull();
        assertThat(result.reliable()).isFalse();
    }

    @Test
    void unavailableWhenPrimaryMetricAbsent() {
        // dimension_w_mm 없이 다른 metric만 있을 때
        var other = new MetricStat("chipping_top_um", 100, 40.0, 2.0, 35.0, 45.0, 40.0, 44.0, 45.0,
                null, null, null, null, null);
        var derived = new DerivedBatchStats(List.of(), List.of(), List.of(other), Map.of(), List.of());
        var result = service.fromLatest(Optional.of(new BatchDetailResponse(null, derived)));

        assertThat(result.cpk()).isNull();
        assertThat(result.sub()).contains("dimension_w_mm");
    }

    private BatchDetailResponse batchWith(MetricStat metric) {
        var derived = new DerivedBatchStats(List.of(), List.of(metric), List.of(), Map.of(), List.of());
        return new BatchDetailResponse(null, derived);
    }
}
