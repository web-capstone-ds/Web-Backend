package com.ds.backend.report.service;

import com.ds.backend.action.entity.ActionLog;
import com.ds.backend.action.repository.ActionLogRepository;
import com.ds.backend.analysis.dto.AiDtos.AlarmHistoryRecord;
import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.HistogramBucket;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.analysis.dto.AiDtos.StatusHistoryRecord;
import com.ds.backend.analysis.service.AiServerClient;
import com.ds.backend.analysis.service.CpkCalculationService;
import com.ds.backend.analysis.service.CpkCalculationService.CpkResult;
import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.equipment.service.EquipmentService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportDataService {
    private static final ZoneId FACTORY_ZONE = ZoneId.of("Asia/Seoul");
    private final EquipmentService equipmentService;
    private final com.ds.backend.equipment.service.RecipeSpecService recipeSpecService;
    private final AiServerClient aiServerClient;
    private final CpkCalculationService cpkCalculationService;
    private final ActionLogRepository actionLogRepository;

    public ReportDataService(EquipmentService equipmentService, com.ds.backend.equipment.service.RecipeSpecService recipeSpecService,
                             AiServerClient aiServerClient, CpkCalculationService cpkCalculationService,
                             ActionLogRepository actionLogRepository) {
        this.equipmentService = equipmentService;
        this.recipeSpecService = recipeSpecService;
        this.aiServerClient = aiServerClient;
        this.cpkCalculationService = cpkCalculationService;
        this.actionLogRepository = actionLogRepository;
    }

    public Map<String, Object> summary() {
        return summary(null, null, "daily", null);
    }

    public Map<String, Object> summary(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(aiQuery(startDate, endDate, reportMode, equipmentId));
        if (aiSummary.isPresent() && hasKpiData(aiSummary.get())) {
            KpiSummaryResponse response = aiSummary.get();
            Optional<BatchDetailResponse> batch = latestBatchFor(startDate, endDate, reportMode, equipmentId);
            CpkResult cpk = cpkForReport(batch, response);
            Map<String, Object> kpi = new LinkedHashMap<>();
            kpi.put("totalProduction", intValue(response.totalUnits()));
            kpi.put("yield", round(doubleValue(response.avgYieldPct())));
            putCpk(kpi, cpk);
            kpi.put("availability", round(doubleValue(response.avgAvailabilityPct())));
            kpi.put("activeAlerts", intValue(response.dangerCount()) + intValue(response.warningCount()));
            kpi.put("mtbf", response.avgMtbfHours() != null ? round(response.avgMtbfHours()) : null);

            double totalHours = periodHours(startDate, endDate);
            Map<String, Object> operationTimeline = new LinkedHashMap<>();
            operationTimeline.put("runHour", round(totalHours * doubleValue(response.avgAvailabilityPct()) / 100.0));
            operationTimeline.put("downHour", round(doubleValue(response.totalDowntimeMin()) / 60.0));
            operationTimeline.put("mtbf", response.avgMtbfHours() != null ? round(response.avgMtbfHours()) : null);
            operationTimeline.put("uph", round(doubleValue(response.avgUph())));
            operationTimeline.put("timeline", buildTimeline(batch));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kpi", kpi);
            result.put("aiMessage", buildAiMessage(response));
            result.put("operationTimeline", operationTimeline);
            result.put("actionPlans", buildActionPlans(startDate, endDate, reportMode, equipmentId));
            return result;
        }
        CpkResult cpk = cpkCalculationService.unavailable("Cpk 계산 불가: AI KPI 집계 데이터 없음");
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalProduction", 0);
        kpi.put("yield", 0.0);
        putCpk(kpi, cpk);
        kpi.put("availability", 0.0);
        kpi.put("activeAlerts", 0);
        kpi.put("mtbf", null);

        Map<String, Object> operationTimeline = new LinkedHashMap<>();
        operationTimeline.put("runHour", 0.0);
        operationTimeline.put("downHour", 0.0);
        operationTimeline.put("mtbf", null);
        operationTimeline.put("uph", 0.0);
        operationTimeline.put("timeline", List.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataAvailable", false);
        result.put("kpi", kpi);
        result.put("aiMessage", "AI 서버 데이터가 없습니다.");
        result.put("operationTimeline", operationTimeline);
        result.put("actionPlans", List.of());
        return result;
    }

    public List<Map<String, Object>> equipments() {
        return equipments(null, null, "daily", null);
    }

    public List<Map<String, Object>> equipments(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        validateReportFilter(reportMode, equipmentId);
        List<Map<String, Object>> equipment = equipmentService.statusList(startDate, endDate, equipmentIdForMode(reportMode, equipmentId));
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
        return equipmentService.defects(startDate, endDate, equipmentIdForMode(reportMode, equipmentId));
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
        Optional<BatchDetailResponse> latest = latestBatchFor(startDate, endDate, reportMode, equipmentId);
        CpkResult cpk = cpkCalculationService.fromLatest(latest);
        if (latest.isPresent() && latest.get().derived() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", qualitySummary(cpk, latest, "AI batch 검사 결과 기반 Pass Rate"));
            result.put("distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                    "histogram", histogram(latest.get(), spec)));
            result.put("aiInference", Map.of("hasAlert", cpk.cpk() != null && cpk.cpk() < 1.33, "title", "AI 치수 이상 원인 추론", "description", oracleComment(latest.get())));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", qualitySummary(cpk, latest, "AI 최신 배치 데이터 없음"));
        result.put("distributionChart", Map.of("guidelines", Map.of("lsl", spec.lsl(), "target", spec.target(), "usl", spec.usl()),
                "histogram", List.of()));
        result.put("aiInference", Map.of("hasAlert", false, "title", "AI 치수 이상 원인 추론", "description", "Cpk 계산 가능한 최신 배치 데이터가 없습니다."));
        return result;
    }

    private Map<String, Object> qualitySummary(CpkResult cpk, Optional<BatchDetailResponse> latest, String passRateSub) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("passRate", passRate(latest));
        summary.put("passRateSub", passRateSub);
        summary.put("cpk", cpk.cpk());
        summary.put("cpkSub", cpk.sub());
        summary.put("status", cpk.status());
        summary.put("cpkReliable", cpk.reliable());
        return summary;
    }

    private double passRate(Optional<BatchDetailResponse> latest) {
        if (latest.isEmpty() || latest.get().batch() == null) {
            return 0.0;
        }
        var batch = latest.get().batch();
        if (batch.lotSummary() != null && intValue(batch.lotSummary().totalUnits()) > 0) {
            int total = intValue(batch.lotSummary().totalUnits());
            int pass = batch.lotSummary().passCount() != null
                    ? intValue(batch.lotSummary().passCount())
                    : Math.max(total - intValue(batch.lotSummary().failCount()), 0);
            return round(pass * 100.0 / total);
        }
        if (batch.records() == null || batch.records().isEmpty()) {
            return 0.0;
        }
        long total = batch.records().stream()
                .filter(record -> record.overallResult() != null || record.overall_result() != null)
                .count();
        if (total == 0) {
            return 0.0;
        }
        long pass = batch.records().stream()
                .filter(record -> "PASS".equalsIgnoreCase(record.overallResult() != null ? record.overallResult() : record.overall_result()))
                .count();
        return round(pass * 100.0 / total);
    }

    private CpkResult cpkForReport(Optional<BatchDetailResponse> batch, KpiSummaryResponse response) {
        if (batch.isPresent()) {
            return cpkCalculationService.fromLatest(batch);
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

    private Optional<BatchDetailResponse> latestBatchFor(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        if (equipmentId != null && !equipmentId.isBlank() && !"all".equalsIgnoreCase(equipmentId)) {
            return equipmentService.batchFor(equipmentId, targetDate(startDate, endDate));
        }
        return equipmentService.statusList(startDate, endDate, "all").stream()
                .map(item -> item.get("id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(id -> !id.isBlank() && !"UNKNOWN".equalsIgnoreCase(id))
                .findFirst()
                .flatMap(id -> equipmentService.batchFor(id, targetDate(startDate, endDate)));
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
            return alarmsFor(equipmentId, targetDate(startDate, endDate));
        }
        // daily/weekly: latestBatch가 장비 단위이므로 장비별 alarmHistory를 합쳐서 반환한다.
        return equipmentService.statusList(startDate, endDate, "all").stream()
                .map(item -> item.get("id"))
                .filter(id -> id instanceof String)
                .flatMap(id -> alarmsFor((String) id, targetDate(startDate, endDate)).stream())
                .toList();
    }

    private List<Map<String, Object>> alarmsFor(String equipmentId) {
        return alarmsFor(equipmentId, null);
    }

    private List<Map<String, Object>> alarmsFor(String equipmentId, LocalDate targetDate) {
        if (equipmentId == null || equipmentId.isBlank()) {
            return List.of();
        }
        Optional<BatchDetailResponse> latest = equipmentService.batchFor(equipmentId, targetDate);
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
        return heatmap(null, null, reportMode, equipmentId);
    }

    public Map<String, Object> heatmap(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        if (!"equipment".equalsIgnoreCase(reportMode) || equipmentId == null || equipmentId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "reportMode=equipment and equipmentId are required for report heatmap");
        }
        Optional<BatchDetailResponse> latest = equipmentService.batchFor(equipmentId, targetDate(startDate, endDate));
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

    private Map<String, Object> aiQuery(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (startDate != null) {
            query.put("from", startDate.atStartOfDay(FACTORY_ZONE).toInstant().toString());
        }
        if (endDate != null) {
            query.put("to", endDate.plusDays(1).atStartOfDay(FACTORY_ZONE).toInstant().toString());
        }
        if ("equipment".equalsIgnoreCase(reportMode) && equipmentId != null && !equipmentId.isBlank()) {
            query.put("equipmentId", equipmentId);
        }
        return query;
    }

    private String equipmentIdForMode(String reportMode, String equipmentId) {
        return "equipment".equalsIgnoreCase(reportMode) && equipmentId != null && !equipmentId.isBlank()
                ? equipmentId
                : "all";
    }

    private LocalDate targetDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null) {
            return endDate;
        }
        return startDate;
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

    private List<Map<String, Object>> histogram(BatchDetailResponse response, com.ds.backend.equipment.service.RecipeSpecService.SpecValues spec) {
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
            double usl = bucket.usl() == null ? spec.usl() : bucket.usl();
            double lsl = bucket.lsl() == null ? spec.lsl() : bucket.lsl();
            item.put("isWarning", end > usl || start < lsl);
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

    private List<Map<String, Object>> buildTimeline(Optional<BatchDetailResponse> batch) {
        if (batch.isEmpty() || batch.get().batch() == null) {
            return List.of();
        }
        List<StatusHistoryRecord> raw = batch.get().batch().statusHistory();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<StatusHistoryRecord> sorted = raw.stream()
                .filter(s -> s.time() != null)
                .sorted(Comparator.comparing(StatusHistoryRecord::time))
                .toList();
        if (sorted.size() < 2) {
            return List.of();
        }
        long totalSec = Math.max(ChronoUnit.SECONDS.between(sorted.get(0).time(), sorted.get(sorted.size() - 1).time()), 1);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            StatusHistoryRecord cur = sorted.get(i);
            StatusHistoryRecord next = sorted.get(i + 1);
            long segSec = ChronoUnit.SECONDS.between(cur.time(), next.time());
            if (segSec <= 0) {
                continue;
            }
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("status", timelineStatus(cur.status()));
            segment.put("start", cur.time().toLocalTime().toString());
            segment.put("end", next.time().toLocalTime().toString());
            segment.put("ratio", round(segSec * 100.0 / totalSec));
            timeline.add(segment);
        }
        return timeline;
    }

    private String timelineStatus(String status) {
        if ("STOP".equalsIgnoreCase(status)) {
            return "error";
        }
        return status == null ? "idle" : status.toLowerCase();
    }

    private List<Map<String, Object>> buildActionPlans(LocalDate startDate, LocalDate endDate, String reportMode, String equipmentId) {
        Specification<ActionLog> spec = Specification.unrestricted();
        if (startDate != null) {
            OffsetDateTime from = startDate.atStartOfDay(FACTORY_ZONE).toOffsetDateTime();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("performedAt"), from));
        }
        if (endDate != null) {
            OffsetDateTime to = endDate.plusDays(1).atStartOfDay(FACTORY_ZONE).toOffsetDateTime();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("performedAt"), to));
        }
        if ("equipment".equalsIgnoreCase(reportMode) && equipmentId != null && !equipmentId.isBlank()) {
            String eqId = equipmentId;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("equipmentId"), eqId));
        }
        return actionLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "performedAt"))
                .stream()
                .map(action -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", action.getActionId().toString());
                    item.put("equipmentId", action.getEquipmentId());
                    item.put("actionType", action.getActionType());
                    item.put("status", action.getActionStatus());
                    item.put("performedBy", action.getPerformedBy());
                    item.put("performedAt", action.getPerformedAt());
                    item.put("note", action.getNote());
                    return item;
                })
                .toList();
    }

    private double periodHours(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return (ChronoUnit.DAYS.between(startDate, endDate) + 1) * 24.0;
        }
        return 24.0;
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
