package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class CpkCalculationService {
    private static final int MIN_SAMPLE_SIZE = 30;
    private static final String SPEC_MAPPING_REQUIRED = "Cpk 계산 불가: recipe spec과 측정 metric 매핑 필요";

    public CpkResult fromLatest(Optional<BatchDetailResponse> latest) {
        if (latest.isEmpty() || latest.get().derived() == null) {
            return unavailable("Cpk 계산 불가: AI 최신 배치 데이터 없음");
        }

        List<MetricStat> metrics = Stream.concat(
                        nullSafe(latest.get().derived().geometricStats()).stream(),
                        nullSafe(latest.get().derived().singulationStats()).stream())
                .toList();
        if (metrics.isEmpty()) {
            return unavailable("Cpk 계산 불가: 측정 metric 데이터 없음");
        }

        Optional<MetricStat> candidate = metrics.stream()
                .filter(this::hasMetricSpecificSpec)
                .filter(this::hasEnoughSamples)
                .filter(this::hasVariance)
                .findFirst();
        if (candidate.isEmpty()) {
            return unavailable(SPEC_MAPPING_REQUIRED);
        }

        MetricStat metric = candidate.get();
        double cpk = Math.min(
                (metric.usl() - metric.mean()) / (3.0 * metric.stdev()),
                (metric.mean() - metric.lsl()) / (3.0 * metric.stdev()));
        return new CpkResult(round(cpk), null, true, metric.metric() + " 기준 Cpk 계산", cpk < 1.33 ? "warning" : "normal");
    }

    public CpkResult unavailable(String reason) {
        return new CpkResult(null, null, false, reason, "unknown");
    }

    private boolean hasMetricSpecificSpec(MetricStat metric) {
        return metric != null
                && metric.metric() != null && !metric.metric().isBlank()
                && metric.mean() != null
                && metric.usl() != null
                && metric.lsl() != null
                && metric.usl() > metric.lsl();
    }

    private boolean hasEnoughSamples(MetricStat metric) {
        return metric.n() != null && metric.n() >= MIN_SAMPLE_SIZE;
    }

    private boolean hasVariance(MetricStat metric) {
        return metric.stdev() != null && metric.stdev() > 0.0;
    }

    private List<MetricStat> nullSafe(List<MetricStat> value) {
        return value == null ? List.of() : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record CpkResult(Double cpk, Double trend, boolean reliable, String sub, String status) {}
}
