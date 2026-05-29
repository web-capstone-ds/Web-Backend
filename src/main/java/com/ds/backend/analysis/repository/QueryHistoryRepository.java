package com.ds.backend.analysis.repository;

import com.ds.backend.analysis.entity.QueryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    Page<QueryHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
