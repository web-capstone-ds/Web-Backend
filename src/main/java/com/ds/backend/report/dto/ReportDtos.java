package com.ds.backend.report.dto;

import com.ds.backend.report.entity.AnalysisReport;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {}

    public record IncomingReportRequest(UUID reportId, String reportType, OffsetDateTime periodStart,
                                        OffsetDateTime periodEnd, String summary, Map<String, Object> content,
                                        OffsetDateTime generatedAt) {}
    public record StoredReportResponse(UUID reportId, String status) {}
    public record ReportResponse(UUID reportId, String reportType, OffsetDateTime generatedAt, String summary, String content) {
        public static ReportResponse from(AnalysisReport report) {
            return new ReportResponse(report.getReportId(), report.getReportType(), report.getGeneratedAt(), report.getSummary(), report.getContent());
        }
    }
}
