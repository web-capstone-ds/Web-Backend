package com.ds.backend.analysis.controller;

import com.ds.backend.analysis.dto.QueryDtos.*;
import com.ds.backend.analysis.service.AnalysisProxyService;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.common.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {
    private final AnalysisProxyService service;

    public AnalysisController(AnalysisProxyService service) {
        this.service = service;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ApiResponse<QueryResponse> query(@AuthenticationPrincipal JwtService.Claims claims, @RequestBody QueryRequest request) {
        return ApiResponse.ok(service.query(claims, request));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ApiResponse<PageResponse<QueryHistoryResponse>> history(@AuthenticationPrincipal JwtService.Claims claims,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(service.history(claims, PageRequest.of(Math.max(page - 1, 0), size))));
    }
}
