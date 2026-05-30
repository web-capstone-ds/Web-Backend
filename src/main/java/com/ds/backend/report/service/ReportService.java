package com.ds.backend.report.service;

import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.audit.service.AuditService;
import com.ds.backend.report.dto.ReportDtos.*;
import com.ds.backend.report.entity.AnalysisReport;
import com.ds.backend.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository, ObjectMapper objectMapper,
                         JwtService jwtService, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public StoredReportResponse store(String authorization, IncomingReportRequest request) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !jwtService.parseServiceToken(authorization.substring("Bearer ".length()))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
        try {
            UUID reportId = request.reportId() == null ? UUID.randomUUID() : request.reportId();
            boolean updated = reportRepository.findByReportId(reportId).isPresent();
            AnalysisReport report = reportRepository.findByReportId(reportId).orElseGet(AnalysisReport::new);
            report.setReportId(reportId);
            report.setReportType(request.reportType() == null ? "daily" : request.reportType());
            report.setPeriodStart(request.periodStart() == null ? OffsetDateTime.now().minusDays(1) : request.periodStart());
            report.setPeriodEnd(request.periodEnd() == null ? OffsetDateTime.now() : request.periodEnd());
            report.setSummary(request.summary() == null ? "" : request.summary());
            report.setContent(objectMapper.writeValueAsString(request.content() == null ? request : request.content()));
            report.setGeneratedAt(request.generatedAt() == null ? OffsetDateTime.now() : request.generatedAt());
            AnalysisReport saved = reportRepository.save(report);
            auditService.record(null, updated ? "UPDATE_REPORT" : "CREATE_REPORT", "report", saved.getReportId().toString());
            return new StoredReportResponse(saved.getReportId(), updated ? "updated" : "stored");
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid report payload");
        }
    }

    public Page<ReportResponse> list(String type, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<AnalysisReport> spec = Specification.unrestricted();
        if (type != null && !"all".equalsIgnoreCase(type)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reportType"), type));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("periodEnd"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("periodStart"), to));
        }
        return reportRepository.findAll(spec, pageable).map(ReportResponse::from);
    }

    public ReportResponse get(JwtService.Claims claims, UUID id) {
        ReportResponse response = reportRepository.findByReportId(id).map(ReportResponse::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Report not found"));
        auditService.record(claims.userId(), "VIEW_REPORT", "report", id.toString());
        return response;
    }

    public AnalysisReport download(JwtService.Claims claims, UUID id) {
        AnalysisReport report = reportRepository.findByReportId(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Report not found"));
        auditService.record(claims.userId(), "VIEW_REPORT", "report", id.toString(), "{\"download\":true}");
        return report;
    }

    public ReportResponse latest(String type) {
        return reportRepository.findFirstByReportTypeOrderByGeneratedAtDesc(type == null ? "daily" : type).map(ReportResponse::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Report not found"));
    }
}
