package com.ds.backend.equipment.controller;

import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.equipment.service.EquipmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/equipments")
@PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
public class EquipmentController {
    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping("/downtime-trend")
    public ApiResponse<Map<String, Object>> downtimeTrend(@RequestParam(required = false) LocalDate startDate,
                                                          @RequestParam(required = false) LocalDate endDate,
                                                          @RequestParam(defaultValue = "all") String equipmentIds) {
        return ApiResponse.ok(service.downtimeTrend(startDate, endDate, equipmentIds));
    }

    @GetMapping("/mtbf")
    public ApiResponse<List<Map<String, Object>>> mtbf(@RequestParam(defaultValue = "all") String equipmentIds) {
        return ApiResponse.ok(service.mtbf(equipmentIds));
    }

    @GetMapping("/defects")
    public ApiResponse<List<Map<String, Object>>> defects() {
        return ApiResponse.ok(service.defects());
    }

    @GetMapping("/status-list")
    public ApiResponse<List<Map<String, Object>>> statusList() {
        return ApiResponse.ok(service.statusList());
    }

    @GetMapping("/{equipmentId}/summary")
    public ApiResponse<Map<String, Object>> detailSummary(@PathVariable String equipmentId) {
        return ApiResponse.ok(service.detailSummary(equipmentId));
    }

    @GetMapping("/{equipmentId}/spc-trend")
    public ApiResponse<List<Map<String, Object>>> spcTrend(@PathVariable String equipmentId) {
        return ApiResponse.ok(service.spcTrend(equipmentId));
    }

    @GetMapping("/{equipmentId}/heatmap")
    public ApiResponse<Map<String, Object>> heatmap(@PathVariable String equipmentId) {
        return ApiResponse.ok(service.heatmap(equipmentId));
    }

    @GetMapping("/{equipmentId}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String equipmentId) {
        return ApiResponse.ok(service.history(equipmentId));
    }
}
