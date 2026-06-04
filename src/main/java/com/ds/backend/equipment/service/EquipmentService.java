package com.ds.backend.equipment.service;

import com.ds.backend.analysis.dto.AiDtos.AlarmHistoryRecord;
import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.BatchListResponse;
import com.ds.backend.analysis.dto.AiDtos.DerivedBatchStats;
import com.ds.backend.analysis.dto.AiDtos.ErrorTypeDistribution;
import com.ds.backend.analysis.dto.AiDtos.EquipmentKpi;
import com.ds.backend.analysis.dto.AiDtos.FailReasonCount;
import com.ds.backend.analysis.dto.AiDtos.GroupedKpi;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryData;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.dto.AiDtos.MetricStat;
import com.ds.backend.analysis.dto.AiDtos.OracleAnalysisRecord;
import com.ds.backend.analysis.dto.AiDtos.SlotAggregate;
import com.ds.backend.analysis.dto.AiDtos.StatusHistoryRecord;
import com.ds.backend.analysis.service.AiServerClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class EquipmentService {
    private static final String DEFAULT_RECIPE = "Carsem_3X3";
    private static final ZoneId FACTORY_ZONE = ZoneId.of("Asia/Seoul");
    private final RecipeSpecService recipeSpecService;
    private final AiServerClient aiServerClient;

    public EquipmentService(RecipeSpecService recipeSpecService, AiServerClient aiServerClient) {
        this.recipeSpecService = recipeSpecService;
        this.aiServerClient = aiServerClient;
    }

    public Map<String, Object> downtimeTrend(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        boolean oneDay = isOneDay(startDate, endDate);
        Optional<KpiSummaryData> aiData = aiServerClient.kpiSummaryData(aiQuery(startDate, endDate, equipmentIds, "day"));
        if (aiData.isPresent() && aiData.get().groups() != null && !aiData.get().groups().isEmpty()) {
            return Map.of(
                    "data", aiData.get().groups().stream()
                            .map(group -> Map.of(
                                    "label", group.displayName(),
                                    "value", round(oneDay ? doubleValue(group.totalDowntimeMin()) : doubleValue(group.totalDowntimeMin()) / 60.0)
                            ))
                            .toList(),
                    "unit", oneDay ? "min" : "hr"
            );
        }

        Optional<KpiSummaryResponse> aiSummary = aiData.map(KpiSummaryData::summary)
                .or(() -> aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds)));
        if (aiSummary.isEmpty()) {
            return Map.of(
                    "dataAvailable", false,
                    "message", "AI 서버 데이터가 없습니다.",
                    "data", List.of(),
                    "unit", oneDay ? "min" : "hr"
            );
        }

        double downtimeMin = doubleValue(aiSummary.get().totalDowntimeMin());
        double value = oneDay ? downtimeMin : downtimeMin / 60.0;
        return Map.of(
                "data", List.of(Map.of("label", downtimeLabel(startDate, endDate, oneDay), "value", round(value))),
                "unit", oneDay ? "min" : "hr"
        );
    }

    public List<Map<String, Object>> mtbf(String equipmentIds) {
        return mtbf(null, null, equipmentIds);
    }

    public List<Map<String, Object>> mtbf(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        Optional<KpiSummaryData> aiData = aiServerClient.kpiSummaryData(aiQuery(startDate, endDate, equipmentIds, isAllEquipment(equipmentIds) ? "equipment" : "day"));
        if (!isAllEquipment(equipmentIds) && aiData.isPresent() && aiData.get().groups() != null && !aiData.get().groups().isEmpty()) {
            return aiData.get().groups().stream()
                    .map(group -> Map.<String, Object>of("name", group.displayName(), "hours", round(doubleValue(group.avgMtbfHours()))))
                    .toList();
        }

        Optional<KpiSummaryResponse> aiSummary = aiData.map(KpiSummaryData::summary)
                .or(() -> aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds)));
        if (aiSummary.isPresent() && aiSummary.get().equipmentDetails() != null && !aiSummary.get().equipmentDetails().isEmpty()) {
            return aiSummary.get().equipmentDetails().stream()
                    .map(equipment -> Map.<String, Object>of("name", equipment.displayId(), "hours", round(doubleValue(equipment.mtbfHours()))))
                    .toList();
        }
        return List.of();
    }

    public List<Map<String, Object>> defects() {
        return defects(null, null, "all");
    }

    public List<Map<String, Object>> defects(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds));
        if (aiSummary.isPresent() && aiSummary.get().topFailReasons() != null && !aiSummary.get().topFailReasons().isEmpty()) {
            int total = aiSummary.get().topFailReasons().stream().mapToInt(reason -> intValue(reason.count())).sum();
            String impact = aiSummary.get().equipmentDetails() == null ? "Oracle ai_comment 기반 영향 분석 필요" : "AI 서버 집계 기반 주요 불량입니다.";
            return aiSummary.get().topFailReasons().stream()
                    .map(reason -> defectItem(reason, total, impact))
                    .toList();
        }
        return List.of();
    }

    public List<Map<String, Object>> statusList() {
        return statusList(null, null, "all");
    }

    public List<Map<String, Object>> statusList(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds));
        if (aiSummary.isPresent() && aiSummary.get().equipmentDetails() != null && !aiSummary.get().equipmentDetails().isEmpty()) {
            return aiSummary.get().equipmentDetails().stream().map(this::toStatusItem).toList();
        }
        Optional<BatchListResponse> batches = aiServerClient.listBatches(aiQuery(startDate, endDate, equipmentIds));
        if (batches.isPresent() && batches.get().items() != null && !batches.get().items().isEmpty()) {
            return batches.get().items().stream()
                    .filter(item -> item.equipmentId() != null || item.equipmentHash() != null)
                    .collect(Collectors.groupingBy(
                            item -> item.equipmentId() == null || item.equipmentId().isBlank() ? item.equipmentHash() : item.equipmentId(),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ))
                    .entrySet().stream()
                    .map(entry -> toStatusItemFromBatches(entry.getKey(), entry.getValue()))
                    .toList();
        }
        return List.of();
    }

    public Map<String, Object> detailSummary(String equipmentId) {
        return detailSummary(equipmentId, null);
    }

    public Map<String, Object> detailSummary(String equipmentId, LocalDate targetDate) {
        Optional<BatchDetailResponse> latest = batchFor(equipmentId, targetDate);
        if (latest.isPresent()) {
            return detailSummaryFromBatch(equipmentId, latest.get());
        }
        return Map.of(
                "dataAvailable", false,
                "message", "AI 서버 데이터가 없습니다.",
                "info", Map.of("recipe", "", "currentLot", "", "status", "NO_DATA"),
                "aiInsight", Map.of("title", "데이터 없음", "description", "AI 서버에 최신 배치 데이터가 없습니다."),
                "uptime", Map.of("totalRate", 0.0, "runHour", 0.0, "idleHour", 0.0, "downHour", 0.0, "timeline", List.of()),
                "parameters", List.of()
        );
    }

    public List<Map<String, Object>> spcTrend(String equipmentId) {
        return spcTrend(equipmentId, null, 7);
    }

    public List<Map<String, Object>> spcTrend(String equipmentId, LocalDate targetDate, int limit) {
        RecipeSpecService.SpecValues spec = recipeSpecService.getSpec(DEFAULT_RECIPE);
        LocalDate endDate = targetDate;
        LocalDate startDate = targetDate == null ? null : targetDate.minusDays(Math.max(limit - 1, 0));
        Optional<BatchListResponse> batches = aiServerClient.listBatches(aiQuery(startDate, endDate, equipmentId));
        if (batches.isPresent() && batches.get().items() != null && !batches.get().items().isEmpty()) {
            double average = batches.get().items().stream()
                    .mapToDouble(item -> doubleValue(item.yieldPct()))
                    .average()
                    .orElse(0.0);
            return batches.get().items().stream()
                    .limit(Math.max(limit, 1))
                    .map(item -> Map.<String, Object>of(
                            "lot", lotDisplay(item.lotHashShort()),
                            "yield", round(doubleValue(item.yieldPct())),
                            "equipAvg", round(average),
                            "lcl", spec.lclYield()
                    ))
                    .toList();
        }
        return List.of();
    }

    public Map<String, Object> heatmap(String equipmentId) {
        return heatmap(equipmentId, null);
    }

    public Map<String, Object> heatmap(String equipmentId, LocalDate targetDate) {
        Optional<BatchDetailResponse> latest = batchFor(equipmentId, targetDate);
        if (latest.isPresent() && latest.get().derived() != null && latest.get().derived().perSlotStats() != null) {
            return Map.of("patternName", patternName(latest.get().derived()), "slots", slots(latest.get().derived()));
        }
        return Map.of("dataAvailable", false, "message", "AI 서버 데이터가 없습니다.", "patternName", "데이터 없음", "slots", List.of());
    }

    public List<Map<String, Object>> history(String equipmentId) {
        return history(equipmentId, null);
    }

    public List<Map<String, Object>> history(String equipmentId, LocalDate targetDate) {
        Optional<BatchDetailResponse> latest = batchFor(equipmentId, targetDate);
        if (latest.isPresent() && latest.get().batch() != null && latest.get().batch().alarmHistory() != null
                && !latest.get().batch().alarmHistory().isEmpty()) {
            return latest.get().batch().alarmHistory().stream()
                    .map(this::historyItem)
                    .toList();
        }
        return List.of();
    }

    public Optional<BatchDetailResponse> batchFor(String equipmentId, LocalDate targetDate) {
        if (targetDate == null) {
            return aiServerClient.latestBatch(equipmentId);
        }
        Optional<BatchListResponse> batches = aiServerClient.listBatches(aiQuery(targetDate, targetDate, equipmentId));
        return batches.flatMap(response -> response.items() == null ? Optional.empty() : response.items().stream()
                .filter(item -> item.batchId() != null && !item.batchId().isBlank())
                .findFirst()
                .flatMap(item -> aiServerClient.getBatch(item.batchId())));
    }

    public List<Map<String, Object>> slots() {
        return List.of();
    }

    public List<Map<String, Object>> slots(DerivedBatchStats derived) {
        Map<Integer, SlotAggregate> bySlot = new LinkedHashMap<>();
        if (derived.perSlotStats() != null) {
            for (SlotAggregate slot : derived.perSlotStats()) {
                if (slot.zAxisNum() != null) {
                    bySlot.put(slot.zAxisNum(), slot);
                }
            }
        }
        return IntStream.range(0, 8).mapToObj(slotNumber -> {
            SlotAggregate slot = bySlot.get(slotNumber);
            int total = slot == null ? 0 : intValue(slot.sideTotal()) + intValue(slot.prsTotal());
            int fail = slot == null ? 0 : intValue(slot.sideFail()) + intValue(slot.prsFail());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("zAxisNum", slotNumber);
            item.put("passCount", Math.max(total - fail, 0));
            item.put("failCount", fail);
            item.put("dominantError", slot == null || slot.dominantErrorType() == null ? null : "ET=" + slot.dominantErrorType());
            item.put("severity", severity(slot == null ? 0.0 : Math.max(doubleValue(slot.sideFailRatePct()), doubleValue(slot.prsFailRatePct()))));
            return item;
        }).toList();
    }

    private Map<String, Object> detailSummaryFromBatch(String equipmentId, BatchDetailResponse response) {
        var batch = response.batch();
        String recipe = batch.lotSummary() == null || batch.lotSummary().recipe() == null ? DEFAULT_RECIPE : batch.lotSummary().recipe();
        RecipeSpecService.SpecValues spec = recipeSpecService.getSpec(recipe);
        OracleAnalysisRecord oracle = latestOracle(response);
        String status = oracle == null || oracle.judgment() == null ? "Normal" : oracle.judgment();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("recipe", spec.recipeId());
        info.put("currentLot", batch.lotDisplay());
        info.put("status", status);

        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("title", "AI 징후 예측 (Pattern Detected)");
        insight.put("description", oracle == null || oracle.comment() == null ? "Oracle ai_comment가 없습니다." : oracle.comment());

        return Map.of(
                "info", info,
                "aiInsight", insight,
                "uptime", uptime(response),
                "parameters", parameters(response.derived(), spec)
        );
    }

    private Map<String, Object> uptime(BatchDetailResponse response) {
        List<StatusHistoryRecord> statuses = response.batch() == null ? List.of() : nullToEmpty(response.batch().statusHistory());
        long run = statuses.stream().filter(status -> "RUN".equalsIgnoreCase(status.status())).count();
        long idle = statuses.stream().filter(status -> "IDLE".equalsIgnoreCase(status.status())).count();
        long stop = statuses.stream().filter(status -> "STOP".equalsIgnoreCase(status.status())).count();
        long total = Math.max(statuses.size(), 1);
        return Map.of(
                "totalRate", round(run * 100.0 / total),
                "runHour", round(run),
                "idleHour", round(idle),
                "downHour", round(stop),
                "timeline", statuses.stream().limit(12).map(status -> Map.<String, Object>of(
                        "status", timelineStatus(status.status()),
                        "start", status.time() == null ? "" : status.time().toLocalTime().toString(),
                        "end", status.time() == null ? "" : status.time().toLocalTime().toString(),
                        "ratio", round(100.0 / total)
                )).toList()
        );
    }

    private List<Map<String, Object>> parameters(DerivedBatchStats derived, RecipeSpecService.SpecValues spec) {
        List<MetricStat> metrics = derived == null ? List.of() : nullToEmpty(derived.singulationStats());
        if (metrics.isEmpty() && derived != null) {
            metrics = nullToEmpty(derived.geometricStats());
        }
        if (metrics.isEmpty()) {
            return List.of();
        }
        return metrics.stream().limit(5).map(metric -> {
            double stdev = doubleValue(metric.stdev());
            double zScore = stdev == 0.0 ? 0.0 : (doubleValue(metric.mean()) - spec.target()) / stdev;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", metric.metric());
            item.put("avg", round(doubleValue(metric.mean())));
            item.put("max", round(doubleValue(metric.max())));
            item.put("usl", spec.usl());
            item.put("zScore", round(zScore));
            item.put("isError", metric.max() != null && metric.max() > spec.usl());
            item.put("cpkReliable", intValue(metric.n()) >= 30);
            return item;
        }).toList();
    }

    private Map<String, Object> toStatusItem(EquipmentKpi equipment) {
        String majorDefect = equipment.topFailReasons() == null || equipment.topFailReasons().isEmpty()
                ? "-"
                : equipment.topFailReasons().get(0).displayCode();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", equipment.displayId());
        item.put("recipe", equipment.recipeId() == null ? DEFAULT_RECIPE : equipment.recipeId());
        item.put("uptime", round(doubleValue(equipment.availabilityPct(), equipment.avgAvailabilityPct())));
        item.put("total", intValue(equipment.totalUnits()));
        item.put("fail", intValue(equipment.totalFail()));
        item.put("marginal", intValue(equipment.marginalCount()));
        item.put("yield", round(equipment.displayYield()));
        item.put("majorDefect", majorDefect);
        item.put("unresolvedAlert", intValue(equipment.alarmCount()) > 0);
        item.put("yieldTrend", List.of(round(equipment.displayYield())));
        return item;
    }

    private Map<String, Object> toStatusItemFromBatches(String equipmentId, List<com.ds.backend.analysis.dto.AiDtos.BatchListItem> batches) {
        int totalUnits = batches.stream().mapToInt(item -> intValue(item.totalUnits())).sum();
        int totalFail = batches.stream().mapToInt(item -> intValue(item.failCount())).sum();
        double weightedYield = weightedYield(batches);
        double availability = batches.stream().mapToDouble(item -> doubleValue(item.availabilityPct())).average().orElse(0.0);
        String recipe = batches.stream()
                .map(item -> item.recipeId())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(DEFAULT_RECIPE);
        int alarmCount = batches.stream().mapToInt(item -> intValue(item.alarmCount())).sum();
        List<Double> yieldTrend = batches.stream()
                .sorted(Comparator.comparing(item -> item.dispatchedAt() == null ? java.time.OffsetDateTime.MIN : item.dispatchedAt()))
                .map(item -> round(doubleValue(item.yieldPct())))
                .toList();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", equipmentId);
        item.put("recipe", recipe);
        item.put("uptime", round(availability));
        item.put("total", totalUnits);
        item.put("fail", totalFail);
        item.put("marginal", 0);
        item.put("yield", round(weightedYield));
        item.put("majorDefect", "-");
        item.put("unresolvedAlert", alarmCount > 0);
        item.put("yieldTrend", yieldTrend.isEmpty() ? List.of(round(weightedYield)) : yieldTrend);
        return item;
    }

    private Map<String, Object> aiQuery(String equipmentIds) {
        if (equipmentIds != null && !equipmentIds.isBlank() && !"all".equalsIgnoreCase(equipmentIds)) {
            return Map.of("equipmentId", equipmentIds.split(",")[0].trim());
        }
        return Map.of();
    }

    private Map<String, Object> aiQuery(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        return aiQuery(startDate, endDate, equipmentIds, null);
    }

    private Map<String, Object> aiQuery(LocalDate startDate, LocalDate endDate, String equipmentIds, String groupBy) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (startDate != null) {
            query.put("from", startDate.atStartOfDay(FACTORY_ZONE).toInstant().toString());
        }
        if (endDate != null) {
            query.put("to", endDate.plusDays(1).atStartOfDay(FACTORY_ZONE).toInstant().toString());
        }
        if (equipmentIds != null && !equipmentIds.isBlank() && !"all".equalsIgnoreCase(equipmentIds)) {
            List.of(equipmentIds.split(",")).stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .ifPresent(value -> query.put("equipmentId", value));
        }
        if (groupBy != null && !groupBy.isBlank()) {
            query.put("groupBy", groupBy);
        }
        return query;
    }

    private boolean isAllEquipment(String equipmentIds) {
        return equipmentIds == null || equipmentIds.isBlank() || "all".equalsIgnoreCase(equipmentIds);
    }

    private boolean isOneDay(LocalDate startDate, LocalDate endDate) {
        return startDate != null && (endDate == null || startDate.equals(endDate));
    }

    private String downtimeLabel(LocalDate startDate, LocalDate endDate, boolean oneDay) {
        if (oneDay && startDate != null) {
            return "%02d/%02d".formatted(startDate.getMonthValue(), startDate.getDayOfMonth());
        }
        if (startDate != null && endDate != null) {
            return "%02d/%02d-%02d/%02d".formatted(
                    startDate.getMonthValue(),
                    startDate.getDayOfMonth(),
                    endDate.getMonthValue(),
                    endDate.getDayOfMonth()
            );
        }
        return "전체";
    }

    private Map<String, Object> defectItem(FailReasonCount reason, int total, String impact) {
        int count = intValue(reason.count());
        return Map.of(
                "code", reason.displayCode(),
                "name", reason.name() == null ? reason.displayCode() : reason.name(),
                "type", "공통 불량",
                "count", count,
                "ratio", total == 0 ? "0%" : Math.round(count * 100.0 / total) + "%",
                "impact", impact
        );
    }

    private String patternName(DerivedBatchStats derived) {
        if (derived.errorTypeDistribution() == null || derived.errorTypeDistribution().isEmpty()) {
            return "8슬롯 결함 분포";
        }
        ErrorTypeDistribution top = derived.errorTypeDistribution().stream()
                .max(Comparator.comparingInt(error -> intValue(error.count())))
                .orElse(null);
        if (top == null || top.errorType() == null) {
            return "8슬롯 결함 분포";
        }
        return "ET=" + top.errorType() + " 집중 패턴";
    }

    private Map<String, Object> historyItem(AlarmHistoryRecord alarm) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", alarm.message_id() == null ? alarm.code() : alarm.message_id());
        item.put("status", "unresolved");
        item.put("time", alarm.time() == null ? "" : alarm.time().toLocalTime().toString());
        item.put("title", alarm.code() == null ? "장비 경보 발생" : alarm.code() + " 경보 발생");
        item.put("description", alarm.detail() == null ? "-" : alarm.detail());
        item.put("worker", null);
        item.put("yieldChange", null);
        return item;
    }

    private OracleAnalysisRecord latestOracle(BatchDetailResponse response) {
        if (response.batch() == null || response.batch().oracleAnalysis() == null || response.batch().oracleAnalysis().isEmpty()) {
            return null;
        }
        return response.batch().oracleAnalysis().get(response.batch().oracleAnalysis().size() - 1);
    }

    private String timelineStatus(String status) {
        if ("STOP".equalsIgnoreCase(status)) {
            return "error";
        }
        return status == null ? "idle" : status.toLowerCase();
    }

    private String severity(double failRatePct) {
        if (failRatePct >= 50.0) {
            return "critical";
        }
        if (failRatePct >= 10.0) {
            return "warning";
        }
        return "info";
    }

    private String lotDisplay(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private double doubleValue(Double preferred) {
        return preferred == null ? 0.0 : preferred;
    }

    private double doubleValue(Double preferred, Double fallback) {
        return preferred == null ? doubleValue(fallback) : preferred;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double weightedYield(List<com.ds.backend.analysis.dto.AiDtos.BatchListItem> batches) {
        int totalUnits = batches.stream().mapToInt(item -> intValue(item.totalUnits())).sum();
        if (totalUnits == 0) {
            return batches.stream().mapToDouble(item -> doubleValue(item.yieldPct())).average().orElse(0.0);
        }
        return batches.stream()
                .mapToDouble(item -> doubleValue(item.yieldPct()) * intValue(item.totalUnits()))
                .sum() / totalUnits;
    }
}
