package com.ds.backend.report.service;

import com.ds.backend.analysis.dto.AiDtos.AlarmHistoryRecord;
import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.HistogramBucket;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.analysis.service.AiServerClient;
import com.ds.backend.analysis.service.CpkCalculationService;
import com.ds.backend.analysis.service.CpkCalculationService.CpkResult;
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
    private final CpkCalculationService cpkCalculationService;

    public ReportDataService(EquipmentService equipmentService, com.ds.backend.equipment.service.RecipeSpecService recipeSpecService,
                             AiServerClient aiServerClient, CpkCalculationService cpkCalculationService) {
        this.equipmentService = equipmentService;
        this.recipeSpecService = recipeSpecService;
        this.aiServerClient = aiServerClient;
        this.cpkCalculationService = cpkCalculationService;
    }

    public Map<String, Object> summary() {
        return summary(null, null, "daily", null);
    }

    public Map<String, Object> summary(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(Map.of());
        if (aiSummary.isPresent() && hasKpiData(aiSummary.get())) {
            KpiSummaryResponse response = aiSummary.get();
            CpkResult cpk = cpkForReport(reportMode, equipmentId, response);
            Map<String, Object> kpi = new LinkedHashMap<>();
            kpi.put("totalProduction", intValue(response.totalUnits()));
            kpi.put("yield", round(doubleValue(response.avgYieldPct())));
            putCpk(kpi, cpk);
            kpi.put("availability", round(doubleValue(response.avgAvailabilityPct())));
            kpi.put("activeAlerts", intValue(response.dangerCount()) + intValue(response.warningCount()));
            kpi.put("mtbf", round(doubleValue(response.avgMtbfHours())));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kpi", kpi);
            result.put("aiMessage", buildAiMessage(response));
            result.put("operationTimeline", Map.of("runHour", 0.0, "downHour", round(doubleValue(response.totalDowntimeMin()) / 60.0), "mtbf", round(doubleValue(response.avgMtbfHours())), "uph", round(doubleValue(response.avgUph())),
                    "timeline", List.of()));
            result.put("actionPlans", List.of());
            return result;
        }
        CpkResult cpk = cpkCalculationService.unavailable("Cpk 계산 불가: AI KPI 집계 데이터 없음");
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalProduction", 0);
        kpi.put("yield", 0.0);
        putCpk(kpi, cpk);
        kpi.put("availability", 0.0);
        kpi.put("activeAlerts", 0);
        kpi.put("mtbf", 0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataAvailable", false);
        result.put("kpi", kpi);
        result.put("aiMessage", "AI 서버 데이터가 없습니다.");
        result.put("operationTimeline", Map.of("runHour", 0.0, "downHour", 0.0, "mtbf", 0.0, "uph", 0.0, "timeline", List.of()));
        result.put("actionPlans", List.of());
        return result;
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
        Optional<BatchDetailResponse> latest = latestBatchFor(reportMode, equipmentId);
        CpkResult cpk = cpkCalculationService.fromLatest(latest);
        if (latest.isPresent() && latest.get().derived() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", qualitySummary(cpk, "PASS drop 정책으로 FAIL 표본 기준"));
            result.put("distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                    "histogram", histogram(latest.get())));
            result.put("aiInference", Map.of("hasAlert", cpk.cpk() != null && cpk.cpk() < 1.33, "title", "AI 치수 이상 원인 추론", "description", oracleComment(latest.get())));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", qualitySummary(cpk, "AI 최신 배치 데이터 없음"));
        result.put("distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                "histogram", List.of()));
        result.put("aiInference", Map.of("hasAlert", false, "title", "AI 치수 이상 원인 추론", "description", "Cpk 계산 가능한 최신 배치 데이터가 없습니다."));
        return result;
    }

    private Map<String, Object> qualitySummary(CpkResult cpk, String passRateSub) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("passRate", cpk.reliable() ? 99.2 : 0.0);
        summary.put("passRateSub", passRateSub);
        summary.put("cpk", cpk.cpk());
        summary.put("cpkSub", cpk.sub());
        summary.put("status", cpk.status());
        summary.put("cpkReliable", cpk.reliable());
        return summary;
    }

    private CpkResult cpkForReport(String reportMode, String equipmentId, KpiSummaryResponse response) {
        Optional<BatchDetailResponse> latest = latestBatchFor(reportMode, equipmentId);
        if (latest.isPresent()) {
            return cpkCalculationService.fromLatest(latest);
        }
        if (response.equipmentDetails() == null) {
            return cpkCalculationService.unavailable("Cpk 계산 불가: 장비 식별자 없음");
        }
        return response.equipmentDetails().stream()
                .map(item -> item.displayId())
                .filter(id -> id != null && !id.isBlank() && !"UNKNOWN".equalsIgnoreCase(id))
                .findFirst()
                .map(id -> cpkCalculationService.fromLatest(aiServerClient.latestBatch(id)))
                .orElseGet(() -> cpkCalculationService.unavailable("Cpk 계산 불가: 장비 식별자 없음"));
    }

    private boolean hasKpiData(KpiSummaryResponse response) {
        return intValue(response.totalUnits()) > 0
                || intValue(response.totalInspected()) > 0
                || (response.equipmentDetails() != null && !response.equipmentDetails().isEmpty())
                || (response.topFailReasons() != null && !response.topFailReasons().isEmpty());
    }

    private Optional<BatchDetailResponse> latestBatchFor(String reportMode, String equipmentId) {
        if (equipmentId != null && !equipmentId.isBlank() && !"all".equalsIgnoreCase(equipmentId)) {
            return aiServerClient.latestBatch(equipmentId);
        }
        return equipmentService.statusList().stream()
                .map(item -> item.get("id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(id -> !id.isBlank() && !"UNKNOWN".equalsIgnoreCase(id))
                .findFirst()
                .flatMap(aiServerClient::latestBatch);
    }

    private void putCpk(Map<String, Object> kpi, CpkResult cpk) {
        kpi.put("cpk", cpk.cpk());
        kpi.put("cpkTrend", cpk.trend());
        kpi.put("cpkReliable", cpk.reliable());
        kpi.put("cpkSub", cpk.sub());
    }

    public List<Map<String, Object>> alarms() {
        return alarms(null, null, "daily", null);
    }

    public List<Map<String, Object>> alarms(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        if ("equipment".equalsIgnoreCase(reportMode)) {
            return alarmsFor(equipmentId);
        }
        // daily/weekly: latestBatch가 장비 단위이므로 장비별 alarmHistory를 합쳐서 반환한다.
        return equipmentService.statusList().stream()
                .map(item -> item.get("id"))
                .filter(id -> id instanceof String)
                .flatMap(id -> alarmsFor((String) id).stream())
                .toList();
    }

    private List<Map<String, Object>> alarmsFor(String equipmentId) {
        if (equipmentId == null || equipmentId.isBlank()) {
            return List.of();
        }
        Optional<BatchDetailResponse> latest = aiServerClient.latestBatch(equipmentId);
        if (latest.isEmpty() || latest.get().batch() == null || latest.get().batch().alarmHistory() == null
                || latest.get().batch().alarmHistory().isEmpty()) {
            return List.of();
        }
        return latest.get().batch().alarmHistory().stream()
                .map(this::alarmItem)
                .toList();
    }

    private Map<String, Object> alarmItem(AlarmHistoryRecord alarm) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", alarm.messageId() != null ? alarm.messageId()
                : (alarm.message_id() != null ? alarm.message_id() : alarm.code()));
        item.put("severity", severityLevel(alarm.level()));
        item.put("eq", alarm.equipmentId() != null ? alarm.equipmentId() : alarm.equipmentHash());
        item.put("message", alarmMessage(alarm));
        item.put("time", alarm.time() == null ? "" : alarm.time().toLocalTime().toString());
        item.put("status", requiresManual(alarm) ? "미조치" : "조치완료");
        item.put("action", "-");
        item.put("worker", "-");
        return item;
    }

    private String alarmMessage(AlarmHistoryRecord alarm) {
        String code = alarm.code();
        String detail = alarm.detail();
        if (code != null && detail != null) {
            return code + ": " + detail;
        }
        if (code != null) {
            return code;
        }
        return detail != null ? detail : "장비 경보 발생";
    }

    private String severityLevel(String level) {
        if (level == null) {
            return "info";
        }
        return switch (level.trim().toUpperCase()) {
            case "CRITICAL", "CRIT", "HIGH", "ERROR" -> "critical";
            case "WARNING", "WARN", "MEDIUM" -> "warning";
            default -> "info";
        };
    }

    private boolean requiresManual(AlarmHistoryRecord alarm) {
        if (alarm.requiresManualIntervention() != null) {
            return alarm.requiresManualIntervention();
        }
        return Boolean.TRUE.equals(alarm.requires_manual_intervention());
    }

    public Map<String, Object> heatmap() {
        return Map.of("dataAvailable", false, "aiAnalysis", Map.of("title", "데이터 없음", "description", "AI 서버 데이터가 없습니다."), "slots", List.of());
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
        return Map.of("dataAvailable", false, "aiAnalysis", Map.of("title", "데이터 없음", "description", "AI 서버 데이터가 없습니다."), "slots", List.of());
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

    private String buildAiMessage(KpiSummaryResponse response) {
        double yield = round(doubleValue(response.avgYieldPct()));
        double availability = round(doubleValue(response.avgAvailabilityPct()));
        int totalUnits = intValue(response.totalUnits());
        int alerts = intValue(response.dangerCount()) + intValue(response.warningCount());
        double mtbf = round(doubleValue(response.avgMtbfHours()));

        String status;
        if (yield >= 95.0 && alerts == 0) {
            status = "정상 운영 중";
        } else if (yield >= 90.0 || alerts <= 2) {
            status = "주의 필요";
        } else {
            status = "즉각 조치 필요";
        }

        return String.format("총 %d개 생산 | 수율 %.1f%% | 가동률 %.1f%% | MTBF %.1fh | 활성 알람 %d건 — %s",
                totalUnits, yield, availability, mtbf, alerts, status);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
