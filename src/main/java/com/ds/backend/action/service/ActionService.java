package com.ds.backend.action.service;

import com.ds.backend.action.dto.ActionDtos.*;
import com.ds.backend.action.entity.ActionLog;
import com.ds.backend.action.repository.ActionLogRepository;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.audit.service.AuditService;
import com.ds.backend.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActionService {
    private final ActionLogRepository repository;
    private final AuditService auditService;

    public ActionService(ActionLogRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public Page<ActionResponse> list(String equipmentId, String status, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<ActionLog> spec = Specification.unrestricted();
        if (equipmentId != null && !equipmentId.isBlank() && !"all".equalsIgnoreCase(equipmentId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("equipmentId"), equipmentId));
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            validateStatus(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actionStatus"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("performedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("performedAt"), to));
        }
        return repository.findAll(spec, pageable).map(ActionResponse::from);
    }

    public ActionResponse get(UUID id) {
        return repository.findById(id).map(ActionResponse::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Action not found"));
    }

    @Transactional
    public ActionResponse create(JwtService.Claims claims, ActionRequest request) {
        ActionLog action = new ActionLog();
        action.setEquipmentId(request.equipmentId());
        action.setAlarmId(request.alarmId());
        action.setActionType(request.actionType());
        action.setPerformedBy(claims.email());
        action.setPerformedAt(request.performedAt() == null ? OffsetDateTime.now() : request.performedAt());
        action.setResultBefore(request.resultBefore());
        action.setResultAfter(request.resultAfter());
        action.setNote(request.note());
        ActionLog saved = repository.save(action);
        auditService.record(claims.userId(), "CREATE_ACTION", "action", saved.getActionId().toString());
        return ActionResponse.from(saved);
    }

    @Transactional
    public ActionResponse update(JwtService.Claims claims, UUID id, ActionStatusRequest request) {
        ActionLog action = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Action not found"));
        validateStatus(request.actionStatus());
        validateTransition(action.getActionStatus(), request.actionStatus());
        action.setActionStatus(request.actionStatus());
        auditService.record(claims.userId(), "UPDATE_ACTION", "action", action.getActionId().toString());
        return ActionResponse.from(action);
    }

    public List<Map<String, Object>> pending() {
        // alarmHistory LEFT JOIN actions_log will populate this after AI/batch data is connected.
        return List.of();
    }

    private void validateStatus(String status) {
        if (!List.of("PENDING", "IN_PROGRESS", "COMPLETED").contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid action status");
        }
    }

    private void validateTransition(String current, String next) {
        if (current.equals(next)) {
            return;
        }
        int currentOrder = List.of("PENDING", "IN_PROGRESS", "COMPLETED").indexOf(current);
        int nextOrder = List.of("PENDING", "IN_PROGRESS", "COMPLETED").indexOf(next);
        if (nextOrder != currentOrder + 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid action status transition");
        }
    }
}
