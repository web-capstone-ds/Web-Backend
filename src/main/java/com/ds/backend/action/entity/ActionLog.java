package com.ds.backend.action.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "actions_log")
public class ActionLog {
    @Id
    @Column(name = "action_id")
    private UUID actionId = UUID.randomUUID();
    @Column(name = "equipment_id", nullable = false)
    private String equipmentId;
    @Column(name = "alarm_id")
    private String alarmId;
    @Column(name = "action_status", nullable = false)
    private String actionStatus = "PENDING";
    @Column(name = "action_type", nullable = false)
    private String actionType;
    @Column(name = "performed_by", nullable = false)
    private String performedBy;
    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt = OffsetDateTime.now();
    @Column(name = "result_before")
    private Double resultBefore;
    @Column(name = "result_after")
    private Double resultAfter;
    private String note;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getActionId() { return actionId; }
    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }
    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String actionStatus) { this.actionStatus = actionStatus; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(OffsetDateTime performedAt) { this.performedAt = performedAt; }
    public Double getResultBefore() { return resultBefore; }
    public void setResultBefore(Double resultBefore) { this.resultBefore = resultBefore; }
    public Double getResultAfter() { return resultAfter; }
    public void setResultAfter(Double resultAfter) { this.resultAfter = resultAfter; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
