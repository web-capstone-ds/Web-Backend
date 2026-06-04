package com.ds.backend.action.dto;

import com.ds.backend.action.entity.ActionLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ActionDtos {
    private ActionDtos() {}
    public record ActionRequest(String equipmentId, String alarmId, String actionType, String actionStatus, String performedBy,
                                OffsetDateTime performedAt, Double resultBefore, Double resultAfter, String note) {}
    public record ActionStatusRequest(String actionStatus, String note) {}
    public record ActionResponse(UUID id, String equipmentId, String alarmId, String actionStatus, String actionType,
                                 String performedBy, OffsetDateTime performedAt, Double resultBefore, Double resultAfter, String note) {
        public static ActionResponse from(ActionLog action) {
            return new ActionResponse(action.getActionId(), action.getEquipmentId(), action.getAlarmId(), action.getActionStatus(),
                    action.getActionType(), action.getPerformedBy(), action.getPerformedAt(), action.getResultBefore(), action.getResultAfter(), action.getNote());
        }
    }
}
