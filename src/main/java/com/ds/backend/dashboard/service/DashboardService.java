package com.ds.backend.dashboard.service;

import com.ds.backend.analysis.dto.AiDtos.BatchListItem;
import com.ds.backend.analysis.dto.AiDtos.BatchListResponse;
import com.ds.backend.analysis.dto.AiDtos.EquipmentKpi;
import com.ds.backend.analysis.dto.AiDtos.FailReasonCount;
import com.ds.backend.analysis.dto.AiDtos.GroupedKpi;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryData;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.service.AiServerClient;
import com.ds.backend.analysis.service.CpkCalculationService;
import com.ds.backend.analysis.service.CpkCalculationService.CpkResult;
import com.ds.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DashboardService {
    private final AiServerClient aiServerClient;
    private final CpkCalculationService cpkCalculationService;

    public DashboardService(AiServerClient aiServerClient, CpkCalculationService cpkCalculationService) {
        this.aiServerClient = aiServerClient;
        this.cpkCalculationService = cpkCalculationService;
    }

    public Map<String, Object> summary(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds, null));
        if (aiSummary.isPresent() && hasKpiData(aiSummary.get())) {
            return toDashboardSummary(aiSummary.get());
        }
        CpkResult cpk = cpkCalculationService.unavailable("Cpk 계산 불가: AI KPI 집계 데이터 없음");
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalProduction", 0);
        kpi.put("uph", 0);
        kpi.put("totalYield", 0.0);
        kpi.put("yieldTrend", 0.0);
        kpi.put("passRate", 0.0);
        putCpk(kpi, cpk);
        kpi.put("topDefect", null);
        kpi.put("availability", 0.0);
        kpi.put("totalDowntimeMin", 0.0);
        kpi.put("mtbfHours", 0.0);
        kpi.put("activeEquipment", 0);
        kpi.put("totalEquipment", 0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dataAvailable", false);
        response.put("message", "AI 서버 데이터가 없습니다.");
        response.put("kpi", kpi);
        response.put("status", Map.of("run", 0.0, "idle", 0.0, "down", 0.0));
        return response;
    }

    public List<Map<String, Object>> trend(LocalDate startDate, LocalDate endDate, String equipmentIds, String unit) {
        if ("monthly".equalsIgnoreCase(unit)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "monthly trend unit is not supported");
        }
        Optional<KpiSummaryData> aiSummary = aiServerClient.kpiSummaryData(aiQuery(startDate, endDate, equipmentIds, "day"));
        if (aiSummary.isPresent() && aiSummary.get().groups() != null && !aiSummary.get().groups().isEmpty()) {
            return aiSummary.get().groups().stream()
                    .map(group -> Map.<String, Object>of(
                            "date", group.displayName(),
                            "production", intValue(group.totalUnits()),
                            "yield", round(group.yieldValue())
                    ))
                    .toList();
        }
        return List.of();
    }

    public List<Map<String, Object>> yieldComparison(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        if ("all".equalsIgnoreCase(equipmentIds)) {
            Optional<KpiSummaryData> aiSummary = aiServerClient.kpiSummaryData(aiQuery(startDate, endDate, equipmentIds, "equipment"));
            if (aiSummary.isPresent() && aiSummary.get().groups() != null && !aiSummary.get().groups().isEmpty()) {
                return aiSummary.get().groups().stream()
                        .map(group -> Map.<String, Object>of("name", group.displayName(), "yield", round(group.yieldValue())))
                        .toList();
            }
        }
        Optional<BatchListResponse> batches = aiServerClient.listBatches(aiQuery(startDate, endDate, equipmentIds, null));
        if (batches.isPresent() && batches.get().items() != null && !batches.get().items().isEmpty()) {
            return batches.get().items().stream()
                    .map(item -> Map.<String, Object>of("name", lotName(item), "yield", round(doubleValue(item.yieldPct()))))
                    .toList();
        }
        return List.of();
    }

    public List<Map<String, Object>> pareto(LocalDate startDate, LocalDate endDate, String equipmentIds) {
        Optional<KpiSummaryResponse> aiSummary = aiServerClient.kpiSummary(aiQuery(startDate, endDate, equipmentIds, null));
        if (aiSummary.isPresent() && aiSummary.get().topFailReasons() != null && !aiSummary.get().topFailReasons().isEmpty()) {
            return toPareto(aiSummary.get().topFailReasons());
        }
        return List.of();
    }

    private Map<String, Object> toDashboardSummary(KpiSummaryResponse response) {
        int totalInspected = intValue(response.totalInspected());
        int totalFail = intValue(response.totalFail());
        double passRate = totalInspected == 0 ? 0.0 : ((totalInspected - totalFail) * 100.0 / totalInspected);
        String topDefect = response.topFailReasons() == null || response.topFailReasons().isEmpty()
                ? "UNKNOWN"
                : response.topFailReasons().get(0).displayCode();
        CpkResult cpk = cpkForSummary(response);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalProduction", intValue(response.totalUnits()));
        kpi.put("uph", round(doubleValue(response.avgUph())));
        kpi.put("totalYield", round(doubleValue(response.avgYieldPct())));
        kpi.put("yieldTrend", 0.0);
        kpi.put("passRate", round(passRate));
        putCpk(kpi, cpk);
        kpi.put("topDefect", topDefect);
        kpi.put("availability", round(doubleValue(response.avgAvailabilityPct())));
        kpi.put("totalDowntimeMin", round(doubleValue(response.totalDowntimeMin())));
        kpi.put("mtbfHours", round(doubleValue(response.avgMtbfHours())));
        kpi.put("activeEquipment", intValue(response.activeEquipmentCount()));
        kpi.put("totalEquipment", intValue(response.totalEquipmentCount()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpi", kpi);
        result.put("status", Map.of(
                "run", round(doubleValue(response.avgAvailabilityPct())),
                "idle", 0.0,
                "down", round(100.0 - doubleValue(response.avgAvailabilityPct()))
        ));
        return result;
    }

    private boolean hasKpiData(KpiSummaryResponse response) {
        return intValue(response.totalUnits()) > 0
                || intValue(response.totalInspected()) > 0
                || (response.equipmentDetails() != null && !response.equipmentDetails().isEmpty())
                || (response.topFailReasons() != null && !response.topFailReasons().isEmpty());
    }

    private CpkResult cpkForSummary(KpiSummaryResponse response) {
        return response.equipmentDetails() == null ? cpkCalculationService.unavailable("Cpk 계산 불가: 장비 식별자 없음")
                : response.equipmentDetails().stream()
                .map(EquipmentKpi::displayId)
                .filter(id -> id != null && !id.isBlank() && !"UNKNOWN".equalsIgnoreCase(id))
                .findFirst()
                .map(id -> cpkCalculationService.fromLatest(aiServerClient.latestBatch(id)))
                .orElseGet(() -> cpkCalculationService.unavailable("Cpk 계산 불가: 장비 식별자 없음"));
    }

    private void putCpk(Map<String, Object> kpi, CpkResult cpk) {
        kpi.put("cpk", cpk.cpk());
        kpi.put("cpkTrend", cpk.trend());
        kpi.put("cpkReliable", cpk.reliable());
        kpi.put("cpkSub", cpk.sub());
    }

    private List<Map<String, Object>> toPareto(List<FailReasonCount> failReasons) {
        int total = failReasons.stream().mapToInt(reason -> intValue(reason.count())).sum();
        AtomicInteger running = new AtomicInteger();
        return failReasons.stream()
                .map(reason -> {
                    int count = intValue(reason.count());
                    int cumulative = total == 0 ? 0 : (int) Math.round(running.addAndGet(count) * 100.0 / total);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("defectCode", reason.displayCode());
                    item.put("defectName", reason.name() == null ? reason.displayCode() : reason.name());
                    item.put("count", count);
                    item.put("cumulative", cumulative);
                    return item;
                })
                .toList();
    }

    // Factory operates in KST; a calendar day picked in the UI means the KST
    // business day. The AI server stores dispatched_at in UTC, so convert the
    // KST day boundary to the equivalent UTC instant before querying.
    private static final ZoneId FACTORY_ZONE = ZoneId.of("Asia/Seoul");

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

    private String lotName(BatchListItem item) {
        if (item.lotHashShort() == null || item.lotHashShort().isBlank()) {
            return "LOT";
        }
        return item.lotHashShort().length() <= 8 ? item.lotHashShort() : item.lotHashShort().substring(0, 8);
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
