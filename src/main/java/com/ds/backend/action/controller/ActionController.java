package com.ds.backend.action.controller;

import com.ds.backend.action.dto.ActionDtos.*;
import com.ds.backend.action.service.ActionService;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.common.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {
    private final ActionService service;

    public ActionController(ActionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<PageResponse<ActionResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestParam(required = false) String equipmentId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) OffsetDateTime from,
                                                          @RequestParam(required = false) OffsetDateTime to) {
        return ApiResponse.ok(PageResponse.from(service.list(equipmentId, status, from, to, PageRequest.of(Math.max(page - 1, 0), size))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ResponseEntity<ApiResponse<ActionResponse>> create(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                                              @RequestBody ActionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(claims, request)));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(service.pending());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ENGINEER','ADMIN')")
    public ApiResponse<ActionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    public ApiResponse<ActionResponse> update(@org.springframework.security.core.annotation.AuthenticationPrincipal JwtService.Claims claims,
                                              @PathVariable UUID id, @RequestBody ActionStatusRequest request) {
        return ApiResponse.ok(service.update(claims, id, request));
    }
}
