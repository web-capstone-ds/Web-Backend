package com.ds.backend.report.controller;

import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.report.service.ReportDataService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
public class ReportDataController {
    private final ReportDataService service;

    public ReportDataController(ReportDataService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) LocalDate startDate,
                                                    @RequestParam(required = false) LocalDate endDate,
                                                    @RequestParam(defaultValue = "daily") String reportMode,
                                                    @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.summary(startDate, endDate, reportMode, equipmentId));
    }

    @GetMapping("/equipments")
    public ApiResponse<List<Map<String, Object>>> equipments(@RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate,
                                                             @RequestParam(defaultValue = "daily") String reportMode,
                                                             @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.equipments(startDate, endDate, reportMode, equipmentId));
    }

    @GetMapping("/defects")
    public ApiResponse<List<Map<String, Object>>> defects(@RequestParam(required = false) LocalDate startDate,
                                                          @RequestParam(required = false) LocalDate endDate,
                                                          @RequestParam(defaultValue = "daily") String reportMode,
                                                          @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.defects(startDate, endDate, reportMode, equipmentId));
    }

    @GetMapping("/quality-distribution")
    public ApiResponse<Map<String, Object>> qualityDistribution(@RequestParam(required = false) LocalDate startDate,
                                                               @RequestParam(required = false) LocalDate endDate,
                                                               @RequestParam(defaultValue = "daily") String reportMode,
                                                               @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.qualityDistribution(startDate, endDate, reportMode, equipmentId));
    }

    @GetMapping("/alarms")
    public ApiResponse<List<Map<String, Object>>> alarms(@RequestParam(required = false) LocalDate startDate,
                                                         @RequestParam(required = false) LocalDate endDate,
                                                         @RequestParam(defaultValue = "daily") String reportMode,
                                                         @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.alarms(startDate, endDate, reportMode, equipmentId));
    }

    @GetMapping("/heatmap")
    public ApiResponse<Map<String, Object>> heatmap(@RequestParam(required = false) LocalDate startDate,
                                                    @RequestParam(required = false) LocalDate endDate,
                                                    @RequestParam(defaultValue = "equipment") String reportMode,
                                                    @RequestParam(required = false) String equipmentId) {
        return ApiResponse.ok(service.heatmap(startDate, endDate, reportMode, equipmentId));
    }
}
