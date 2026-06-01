package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.DerivedBatchStats;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CpkCalculationServiceTest {
    private final CpkCalculationService service = new CpkCalculationService();

    @Test
    void returnsNullWhenLatestBatchIsMissing() {
        var result = service.fromLatest(Optional.empty());

        assertThat(result.cpk()).isNull();
        assertThat(result.trend()).isNull();
        assertThat(result.reliable()).isFalse();
    }

    @Test
    void returnsNullWhenMetricSpecificSpecIsMissing() {
        var metric = new MetricStat("dimension_w_mm", 50, 10.04, 0.03, 10.0, 10.1, 10.04, 10.08, 10.09,
                null, null, null, null, null);
        var result = service.fromLatest(Optional.of(batchWith(metric)));

        assertThat(result.cpk()).isNull();
        assertThat(result.reliable()).isFalse();
        assertThat(result.sub()).contains("recipe spec");
    }

    @Test
    void returnsNullWhenSampleSizeIsTooSmall() {
        var metric = new MetricStat("dimension_w_mm", 29, 12.0, 0.01, 11.99, 12.01, 12.0, 12.01, 12.01,
                12.04, 11.96, null, null, null);
        var result = service.fromLatest(Optional.of(batchWith(metric)));

        assertThat(result.cpk()).isNull();
        assertThat(result.reliable()).isFalse();
    }

    @Test
    void calculatesCpkWhenMetricSpecificSpecIsAvailable() {
        var metric = new MetricStat("dimension_w_mm", 30, 12.0, 0.01, 11.99, 12.01, 12.0, 12.01, 12.01,
                12.04, 11.96, null, null, null);
        var result = service.fromLatest(Optional.of(batchWith(metric)));

        assertThat(result.cpk()).isEqualTo(1.33);
        assertThat(result.reliable()).isTrue();
        assertThat(result.status()).isEqualTo("normal");
    }

    private BatchDetailResponse batchWith(MetricStat metric) {
        var derived = new DerivedBatchStats(List.of(), List.of(metric), List.of(), Map.of(), List.of());
        return new BatchDetailResponse(null, derived);
    }
}
