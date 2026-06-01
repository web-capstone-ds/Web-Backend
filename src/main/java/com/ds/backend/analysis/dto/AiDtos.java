package com.ds.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AiDtos {
    private AiDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchEnvelope<T>(
            String status,
            String requestId,
            OffsetDateTime servedAt,
            T data,
            AiError error
    ) {
        public boolean ok() {
            return "ok".equalsIgnoreCase(status) && data != null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiError(String code, String message, Map<String, Object> details) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchListResponse(List<BatchListItem> items, BatchPage page) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchPage(Integer number, Integer size, Long totalElements, Integer totalPages, Boolean hasNext) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchListItem(
            String batchId,
            String equipmentId,
            String equipmentHash,
            String lotHashShort,
            String recipeId,
            String lotStatus,
            OffsetDateTime dispatchedAt,
            OffsetDateTime lotEndAt,
            Double yieldPct,
            Integer totalUnits,
            Integer failCount,
            String judgment,
            Integer severityCode,
            Integer alarmCount,
            Double availabilityPct
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchDetailResponse(DispatchBatch batch, DerivedBatchStats derived) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DispatchBatch(
            String batchId,
            OffsetDateTime dispatchedAt,
            String lotHash,
            String equipmentHash,
            String equipmentId,
            Integer totalRecords,
            List<InspectionRecord> records,
            LotSummary lotSummary,
            List<OracleAnalysisRecord> oracleAnalysis,
            List<StatusHistoryRecord> statusHistory,
            List<AlarmHistoryRecord> alarmHistory
    ) {
        public String lotDisplay() {
            if (lotHash == null || lotHash.length() < 8) {
                return lotHash == null ? "" : lotHash;
            }
            return lotHash.substring(0, 8);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InspectionRecord(
            String messageId,
            String message_id,
            String lotHash,
            String equipmentHash,
            String equipmentId,
            Integer stripId,
            Integer strip_id,
            Integer unitId,
            Integer unit_id,
            String overallResult,
            String overall_result,
            OffsetDateTime time,
            String failReasonCode,
            String fail_reason_code,
            Integer failCount,
            Integer fail_count,
            Integer totalInspectedCount,
            Integer total_inspected_count,
            Integer inspectionDurationMs,
            Integer inspection_duration_ms,
            Integer taktTimeMs,
            Integer takt_time_ms,
            String algorithmVersion,
            String algorithm_version,
            JsonNode inspectionDetail,
            JsonNode inspection_detail,
            JsonNode geometric,
            JsonNode bga,
            JsonNode surface,
            JsonNode singulation
    ) {
        public JsonNode detail() {
            return inspectionDetail != null ? inspectionDetail : inspection_detail;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LotSummary(
            String lotHash,
            String equipmentHash,
            String lotStatus,
            String lot_status,
            String recipeId,
            String recipe_id,
            Integer totalUnits,
            Integer total_units,
            Integer passCount,
            Integer pass_count,
            Integer failCount,
            Integer fail_count,
            Double yieldPct,
            Double yield_pct,
            Integer lotDurationSec,
            Integer lot_duration_sec
    ) {
        public String recipe() {
            return recipeId != null ? recipeId : recipe_id;
        }

        public double yieldValue() {
            return yieldPct != null ? yieldPct : value(yield_pct);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OracleAnalysisRecord(
            String messageId,
            String message_id,
            OffsetDateTime time,
            String lotIdHash,
            String lot_id_hash,
            String lotHash,
            String equipmentHash,
            String equipmentId,
            String judgment,
            Double yieldPct,
            Double yield_pct,
            Double yieldActual,
            Double yield_actual,
            String aiComment,
            String ai_comment,
            JsonNode violatedRules,
            JsonNode violated_rules,
            String analysisSource,
            String analysis_source
    ) {
        public String comment() {
            return aiComment != null ? aiComment : ai_comment;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusHistoryRecord(
            String messageId,
            String message_id,
            OffsetDateTime time,
            String equipmentHash,
            String equipmentId,
            String equipmentStatus,
            String equipment_status,
            String lotId,
            String lot_id,
            String recipeId,
            String recipe_id,
            Integer uptimeSec,
            Integer uptime_sec,
            Integer currentUnitCount,
            Integer current_unit_count,
            Integer expectedTotalUnits,
            Integer expected_total_units,
            Double currentYieldPct,
            Double current_yield_pct
    ) {
        public String status() {
            return equipmentStatus != null ? equipmentStatus : equipment_status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlarmHistoryRecord(
            String messageId,
            String message_id,
            OffsetDateTime time,
            String equipmentHash,
            String equipmentId,
            String alarmLevel,
            String alarm_level,
            String hwErrorCode,
            String hw_error_code,
            String hwErrorSource,
            String hw_error_source,
            String hwErrorDetail,
            String hw_error_detail,
            Boolean autoRecoveryAttempted,
            Boolean auto_recovery_attempted,
            Boolean requiresManualIntervention,
            Boolean requires_manual_intervention,
            String burstId,
            String burst_id,
            Integer burstCount,
            Integer burst_count
    ) {
        public String level() {
            return alarmLevel != null ? alarmLevel : alarm_level;
        }

        public String code() {
            return hwErrorCode != null ? hwErrorCode : hw_error_code;
        }

        public String detail() {
            return hwErrorDetail != null ? hwErrorDetail : hw_error_detail;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DerivedBatchStats(
            List<SlotAggregate> perSlotStats,
            List<MetricStat> geometricStats,
            List<MetricStat> singulationStats,
            Map<String, HistogramBucket> histogramBuckets,
            List<ErrorTypeDistribution> errorTypeDistribution
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlotAggregate(
            Integer zAxisNum,
            Integer prsTotal,
            Integer prsFail,
            Double prsFailRatePct,
            Integer sideTotal,
            Integer sideFail,
            Double sideFailRatePct,
            Integer dominantErrorType,
            Double xOffsetP95,
            Double yOffsetP95
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetricStat(
            String metric,
            Integer n,
            Double mean,
            Double stdev,
            Double min,
            Double max,
            Double p50,
            Double p95,
            Double p99,
            Double usl,
            Double lsl,
            Double cp,
            Double cpk,
            Double inSpecPct
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistogramBucket(
            List<Double> bucketEdges,
            List<Integer> counts,
            Double mean,
            Double usl,
            Double lsl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorTypeDistribution(
            Integer errorType,
            Integer count,
            Double ratio,
            List<Integer> affectedSlots,
            String side
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KpiSummaryData(
            Map<String, Object> period,
            KpiSummaryResponse summary,
            List<GroupedKpi> groups
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KpiSummaryResponse(
            Map<String, Object> period,
            Integer totalUnits,
            Integer totalInspected,
            Integer totalFail,
            Double avgYieldPct,
            Double avgUph,
            Integer marginalCount,
            Integer dangerCount,
            Integer warningCount,
            Double avgAvailabilityPct,
            Double totalDowntimeMin,
            Integer activeEquipmentCount,
            Integer totalEquipmentCount,
            Double avgMtbfHours,
            List<FailReasonCount> topFailReasons,
            List<EquipmentKpi> equipmentDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupedKpi(
            String key,
            String label,
            String name,
            Integer totalUnits,
            Integer totalFail,
            Double avgYieldPct,
            Double yieldPct,
            Double avgUph,
            Double avgAvailabilityPct,
            Double totalDowntimeMin,
            Double avgMtbfHours,
            Integer activeEquipmentCount,
            Integer totalEquipmentCount,
            List<FailReasonCount> topFailReasons
    ) {
        public String displayName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            if (label != null && !label.isBlank()) {
                return label;
            }
            return key == null ? "UNKNOWN" : key;
        }

        public double yieldValue() {
            return yieldPct != null ? yieldPct : value(avgYieldPct);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailReasonCount(
            String failReasonCode,
            String fail_reason_code,
            String reason_code,
            String code,
            String name,
            Integer count
    ) {
        public String displayCode() {
            if (failReasonCode != null && !failReasonCode.isBlank()) {
                return failReasonCode;
            }
            if (fail_reason_code != null && !fail_reason_code.isBlank()) {
                return fail_reason_code;
            }
            if (reason_code != null && !reason_code.isBlank()) {
                return reason_code;
            }
            return code == null || code.isBlank() ? "UNKNOWN" : code;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EquipmentKpi(
            String equipmentId,
            String equipmentHash,
            String recipeId,
            Integer totalUnits,
            Integer totalFail,
            Double yieldPct,
            Double avgYieldPct,
            Double uph,
            Double avgUph,
            Double availabilityPct,
            Double avgAvailabilityPct,
            Double downtimeMin,
            Double mtbfHours,
            Integer alarmCount,
            Integer marginalCount,
            List<FailReasonCount> topFailReasons
    ) {
        public String displayId() {
            if (equipmentId != null && !equipmentId.isBlank()) {
                return equipmentId;
            }
            return equipmentHash == null || equipmentHash.isBlank() ? "UNKNOWN" : equipmentHash;
        }

        public double displayYield() {
            return yieldPct != null ? yieldPct : value(avgYieldPct);
        }
    }

    private static double value(Double value) {
        return value == null ? 0.0 : value;
    }
}
