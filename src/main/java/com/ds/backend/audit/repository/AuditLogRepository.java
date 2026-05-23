package com.ds.backend.audit.repository;

import com.ds.backend.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    long countByAction(String action);
}
