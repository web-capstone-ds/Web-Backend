package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.equipment.service.RecipeSpecService;
import com.ds.backend.equipment.service.RecipeSpecService.SpecValues;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class CpkCalculationService {
    private static final int MIN_SAMPLE_SIZE = 30;
    // Cpk 산정 대상 측정값 — recipe_specs(USL/LSL)이 패키지 폭(dimension_w_mm) 기준이다.
    private static final String PRIMARY_METRIC = "dimension_w_mm";

    private final RecipeSpecService recipeSpecService;
    // recipe_id는 dispatcher 익명화로 배치에 평문이 없으므로, 설정된 기본 레시피 spec을 사용한다.
    private final String defaultRecipeId;

    public CpkCalculationService(RecipeSpecService recipeSpecService,
                                 @Value("${cpk.default-recipe-id:Carsem_3X3}") String defaultRecipeId) {
        this.recipeSpecService = recipeSpecService;
        this.defaultRecipeId = defaultRecipeId;
    }

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

        Optional<MetricStat> primary = metrics.stream()
                .filter(m -> PRIMARY_METRIC.equals(m.metric()))
                .filter(this::hasDistribution)
                .findFirst();
        if (primary.isEmpty()) {
            return unavailable("Cpk 계산 불가: " + PRIMARY_METRIC + " 측정 분포 데이터 없음");
        }

        MetricStat metric = primary.get();
        SpecValues spec = recipeSpecService.getSpec(defaultRecipeId);
        // metric 자체 규격이 있으면 우선, 없으면 recipe spec(USL/LSL) 사용
        double usl = metric.usl() != null ? metric.usl() : spec.usl();
        double lsl = metric.lsl() != null ? metric.lsl() : spec.lsl();
        if (usl <= lsl) {
            return unavailable("Cpk 계산 불가: recipe spec USL/LSL 범위 오류");
        }

        double cpk = Math.min(
                (usl - metric.mean()) / (3.0 * metric.stdev()),
                (metric.mean() - lsl) / (3.0 * metric.stdev()));
        boolean reliable = metric.n() != null && metric.n() >= MIN_SAMPLE_SIZE;
        String sub = metric.metric() + " 기준 Cpk (USL " + spec.usl() + " / LSL " + spec.lsl() + ")"
                + (reliable ? "" : ", 표본 부족 n<" + MIN_SAMPLE_SIZE);
        return new CpkResult(round(cpk), null, reliable, sub, cpk < 1.33 ? "warning" : "normal");
    }

    public CpkResult unavailable(String reason) {
        return new CpkResult(null, null, false, reason, "unknown");
    }

    private boolean hasDistribution(MetricStat metric) {
        return metric != null
                && metric.mean() != null
                && metric.stdev() != null
                && metric.stdev() > 0.0;
    }

    private List<MetricStat> nullSafe(List<MetricStat> value) {
        return value == null ? List.of() : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record CpkResult(Double cpk, Double trend, boolean reliable, String sub, String status) {}
}
