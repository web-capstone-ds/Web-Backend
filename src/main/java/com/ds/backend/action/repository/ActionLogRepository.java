package com.ds.backend.action.repository;

import com.ds.backend.action.entity.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ActionLogRepository extends JpaRepository<ActionLog, UUID>, JpaSpecificationExecutor<ActionLog> {
}
