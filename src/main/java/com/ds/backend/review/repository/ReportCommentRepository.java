package com.ds.backend.review.repository;

import com.ds.backend.review.entity.ReportComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReportCommentRepository extends JpaRepository<ReportComment, UUID> {
    List<ReportComment> findByReportDateOrderByCreatedAtDesc(LocalDate reportDate);
}
