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
    public ApiResponse<List<Map<String, Object>>> mtbf(@RequestParam(defaultValue = "all") String equipmentIds,
                                                       @RequestParam(required = false) LocalDate startDate,
                                                       @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(service.mtbf(startDate, endDate, equipmentIds));
    }

    @GetMapping("/defects")
    public ApiResponse<List<Map<String, Object>>> defects(@RequestParam(defaultValue = "all") String equipmentIds,
                                                          @RequestParam(required = false) LocalDate startDate,
                                                          @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(service.defects(startDate, endDate, equipmentIds));
    }

    @GetMapping("/status-list")
    public ApiResponse<List<Map<String, Object>>> statusList(@RequestParam(defaultValue = "all") String equipmentIds,
                                                             @RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(service.statusList(startDate, endDate, equipmentIds));
    }

    @GetMapping("/{equipmentId}/summary")
    public ApiResponse<Map<String, Object>> detailSummary(@PathVariable String equipmentId,
                                                          @RequestParam(required = false) LocalDate targetDate) {
        return ApiResponse.ok(service.detailSummary(equipmentId, targetDate));
    }

    @GetMapping("/{equipmentId}/spc-trend")
    public ApiResponse<List<Map<String, Object>>> spcTrend(@PathVariable String equipmentId,
                                                           @RequestParam(required = false) LocalDate targetDate,
                                                           @RequestParam(defaultValue = "7") int limit) {
        return ApiResponse.ok(service.spcTrend(equipmentId, targetDate, limit));
    }

    @GetMapping("/{equipmentId}/heatmap")
    public ApiResponse<Map<String, Object>> heatmap(@PathVariable String equipmentId,
                                                    @RequestParam(required = false) LocalDate targetDate) {
        return ApiResponse.ok(service.heatmap(equipmentId, targetDate));
    }

    @GetMapping("/{equipmentId}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String equipmentId,
                                                          @RequestParam(required = false) LocalDate targetDate) {
        return ApiResponse.ok(service.history(equipmentId, targetDate));
    }
}
