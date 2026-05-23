package com.ds.backend.report.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.HistogramBucket;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.analysis.service.AiServerClient;
import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.equipment.service.EquipmentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportDataService {
    private final EquipmentService equipmentService;
    private final com.ds.backend.equipment.service.RecipeSpecService recipeSpecService;
    private final AiServerClient aiServerClient;

    public ReportDataService(EquipmentService equipmentService, com.ds.backend.equipment.service.RecipeSpecService recipeSpecService,
                             AiServerClient aiServerClient) {
        this.equipmentService = equipmentService;
        this.recipeSpecService = recipeSpecService;
        this.aiServerClient = aiServerClient;
    }

    public Map<String, Object> summary() {
        return summary(null, null, "daily", null);
    }

    public Map<String, Object> summary(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(Map.of());
        if (aiSummary.isPresent()) {
            KpiSummaryResponse response = aiSummary.get();
            return Map.of(
                    "kpi", Map.of(
                            "totalProduction", intValue(response.totalUnits()),
                            "yield", round(doubleValue(response.avgYieldPct())),
                            "cpk", 0.0,
                            "availability", round(doubleValue(response.avgAvailabilityPct())),
                            "activeAlerts", intValue(response.dangerCount()) + intValue(response.warningCount()),
                            "mtbf", round(doubleValue(response.avgMtbfHours()))
                    ),
                    "aiMessage", "AI 서버 KPI 집계 기준으로 생성된 리포트 요약입니다.",
                    "operationTimeline", Map.of("runHour", 0.0, "downHour", round(doubleValue(response.totalDowntimeMin()) / 60.0), "mtbf", round(doubleValue(response.avgMtbfHours())), "uph", round(doubleValue(response.avgUph())),
                            "timeline", List.of()),
                    "actionPlans", List.of()
            );
        }
        return Map.of(
                "kpi", Map.of("totalProduction", 24563, "yield", 98.7, "cpk", 1.52, "availability", 87.3, "activeAlerts", 4, "mtbf", 91.6),
                "aiMessage", "금일 주간 가동 결과, 전체 생산량은 안정권입니다.",
                "operationTimeline", Map.of("runHour", 102.5, "downHour", 3.2, "mtbf", 42.5, "uph", 2850,
                        "timeline", List.of(Map.of("status", "run", "start", "08:00", "end", "10:24", "ratio", 20))),
                "actionPlans", List.of(Map.of("priority", 1, "title", "SAW-EQ.01 1번 스핀들 블레이드 즉시 교체", "description", "미조치 경보와 관련하여 점검 필요", "isCritical", true))
        );
    }

    public List<Map<String, Object>> equipments() {
        return equipments(null, null, "daily", null);
    }

    public List<Map<String, Object>> equipments(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        List<Map<String, Object>> equipment = equipmentService.statusList();
        if ("equipment".equalsIgnoreCase(reportMode)) {
            return equipment.stream()
                    .filter(item -> equipmentId.equals(item.get("id")))
                    .toList();
        }
        return equipment;
    }

    public List<Map<String, Object>> defects() {
        return defects(null, null, "daily", null);
    }

    public List<Map<String, Object>> defects(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        return equipmentService.defects();
    }

    public Map<String, Object> qualityDistribution() {
        return qualityDistribution(null);
    }

    public Map<String, Object> qualityDistribution(String equipmentId) {
        return qualityDistribution(null, null, equipmentId == null ? "daily" : "equipment", equipmentId);
    }

    public Map<String, Object> qualityDistribution(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        var spec = recipeSpecService.getSpec("Carsem_3X3");
        Optional<BatchDetailResponse> latest = aiServerClient.latestBatch(equipmentId);
        if (latest.isPresent() && latest.get().derived() != null) {
            Optional<MetricStat> metric = firstMetric(latest.get());
            double cpk = metric.map(value -> cpk(value, spec.lsl(), spec.usl())).orElse(0.0);
            return Map.of(
                    "summary", Map.of("passRate", 99.2, "passRateSub", "PASS drop 정책으로 FAIL 표본 기준", "cpk", round(cpk), "cpkSub", "recipe_specs 기준 계산", "status", cpk < 1.33 ? "warning" : "info", "cpkReliable", metric.map(value -> intValue(value.n()) >= 30).orElse(false)),
                    "distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                            "histogram", histogram(latest.get())),
                    "aiInference", Map.of("hasAlert", cpk < 1.33, "title", "AI 치수 이상 원인 추론", "description", oracleComment(latest.get()))
            );
        }
        return Map.of(
                "summary", Map.of("passRate", 99.2, "passRateSub", "목표 99.0% (초과 달성)", "cpk", 1.38, "cpkSub", "상한계(USL) 방향 편차 발생 중", "status", "warning"),
                "distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                        "histogram", List.of(Map.of("range", "11.96 미만", "count", 10, "isWarning", false), Map.of("range", "12.04-12.05", "count", 15, "isWarning", true))),
                "aiInference", Map.of("hasAlert", true, "title", "AI 치수 이상 원인 추론", "description", "Oracle ai_comment 기반 치수 이상 추론입니다.")
        );
    }

    public List<Map<String, Object>> alarms() {
        return alarms(null, null, "daily", null);
    }

    public List<Map<String, Object>> alarms(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        return List.of();
    }

    public Map<String, Object> heatmap() {
        return heatmap("equipment", "SAW-EQ.01");
    }

    public Map<String, Object> heatmap(String reportMode, String equipmentId) {
        if (!"equipment".equalsIgnoreCase(reportMode) || equipmentId == null || equipmentId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "reportMode=equipment and equipmentId are required for report heatmap");
        }
        Optional<BatchDetailResponse> latest = aiServerClient.latestBatch(equipmentId);
        if (latest.isPresent() && latest.get().derived() != null) {
            return Map.of("aiAnalysis", Map.of("title", "8슬롯 결함 패턴 분석", "description", oracleComment(latest.get())),
                    "slots", equipmentService.slots(latest.get().derived()));
        }
        return Map.of("aiAnalysis", Map.of("title", "슬롯 6~7 ET=12 집중 패턴 감지", "description", "ZAxisNum 6~7에 ET=12 결함이 집중되었습니다."),
                "slots", equipmentService.slots());
    }

    private void validateReportFilter(String reportMode, String equipmentId) {
        if ("equipment".equalsIgnoreCase(reportMode) && (equipmentId == null || equipmentId.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "equipmentId is required when reportMode=equipment");
        }
        if (!List.of("daily", "weekly", "equipment").contains(reportMode == null ? "daily" : reportMode.toLowerCase())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid reportMode");
        }
    }

    private Optional<MetricStat> firstMetric(BatchDetailResponse response) {
        if (response.derived().geometricStats() != null && !response.derived().geometricStats().isEmpty()) {
            return Optional.of(response.derived().geometricStats().get(0));
        }
        if (response.derived().singulationStats() != null && !response.derived().singulationStats().isEmpty()) {
            return Optional.of(response.derived().singulationStats().get(0));
        }
        return Optional.empty();
    }

    private List<Map<String, Object>> histogram(BatchDetailResponse response) {
        if (response.derived().histogramBuckets() == null || response.derived().histogramBuckets().isEmpty()) {
            return List.of();
        }
        HistogramBucket bucket = response.derived().histogramBuckets().values().iterator().next();
        if (bucket.bucketEdges() == null || bucket.counts() == null) {
            return List.of();
        }
        int size = Math.min(bucket.counts().size(), Math.max(bucket.bucketEdges().size() - 1, 0));
        return java.util.stream.IntStream.range(0, size).mapToObj(index -> {
            double start = bucket.bucketEdges().get(index);
            double end = bucket.bucketEdges().get(index + 1);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("range", round(start) + "-" + round(end));
            item.put("count", bucket.counts().get(index));
            item.put("isWarning", (bucket.usl() != null && end > bucket.usl()) || (bucket.lsl() != null && start < bucket.lsl()));
            return item;
        }).toList();
    }

    private double cpk(MetricStat metric, double lsl, double usl) {
        double stdev = doubleValue(metric.stdev());
        if (stdev == 0.0) {
            return 0.0;
        }
        double mean = doubleValue(metric.mean());
        return Math.min((usl - mean) / (3.0 * stdev), (mean - lsl) / (3.0 * stdev));
    }

    private String oracleComment(BatchDetailResponse response) {
        if (response.batch() == null || response.batch().oracleAnalysis() == null || response.batch().oracleAnalysis().isEmpty()) {
            return "Oracle ai_comment가 없습니다.";
        }
        return response.batch().oracleAnalysis().stream()
                .map(analysis -> analysis.comment())
                .filter(comment -> comment != null && !comment.isBlank())
                .findFirst()
                .orElse("Oracle ai_comment가 없습니다.");
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private double doubleValue(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
