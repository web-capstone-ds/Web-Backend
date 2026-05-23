package com.ds.backend.report.controller;

import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.common.dto.PageResponse;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.report.dto.ReportDtos.*;
import com.ds.backend.report.entity.AnalysisReport;
import com.ds.backend.report.service.ReportService;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoredReportResponse>> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                     @RequestBody IncomingReportRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(reportService.store(authorization, request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<PageResponse<ReportResponse>> list(@RequestParam(defaultValue = "all") String type,
                                                          @RequestParam(required = false) OffsetDateTime from,
                                                          @RequestParam(required = false) OffsetDateTime to,
                                                          @RequestParam(defaultValue = "desc") String sort,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ApiResponse.ok(PageResponse.from(reportService.list(type, from, to,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(direction, "generatedAt")))));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<ReportResponse> latest(@RequestParam(defaultValue = "daily") String type) {
        return ApiResponse.ok(reportService.latest(type));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<ReportResponse> get(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                           @PathVariable UUID id) {
        return ApiResponse.ok(reportService.get(claims, id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ResponseEntity<String> download(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                           @PathVariable UUID id) {
        AnalysisReport report = reportService.download(claims, id);
        String filename = report.getReportType() + "-" + report.getReportId() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(report.getContent());
    }
}
