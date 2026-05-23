package com.ds.backend.dashboard.controller;

import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) LocalDate startDate,
                                                    @RequestParam(required = false) LocalDate endDate,
                                                    @RequestParam(defaultValue = "all") String equipmentIds) {
        return ApiResponse.ok(service.summary(startDate, endDate, equipmentIds));
    }

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(required = false) LocalDate startDate,
                                                        @RequestParam(required = false) LocalDate endDate,
                                                        @RequestParam(defaultValue = "all") String equipmentIds,
                                                        @RequestParam(defaultValue = "daily") String unit) {
        return ApiResponse.ok(service.trend(startDate, endDate, equipmentIds, unit));
    }

    @GetMapping("/yield-comparison")
    public ApiResponse<List<Map<String, Object>>> yieldComparison(@RequestParam(required = false) LocalDate startDate,
                                                                  @RequestParam(required = false) LocalDate endDate,
                                                                  @RequestParam(defaultValue = "all") String equipmentIds) {
        return ApiResponse.ok(service.yieldComparison(startDate, endDate, equipmentIds));
    }

    @GetMapping("/defects/pareto")
    public ApiResponse<List<Map<String, Object>>> pareto(@RequestParam(required = false) LocalDate startDate,
                                                         @RequestParam(required = false) LocalDate endDate,
                                                         @RequestParam(defaultValue = "all") String equipmentIds) {
        return ApiResponse.ok(service.pareto(startDate, endDate, equipmentIds));
    }
}
