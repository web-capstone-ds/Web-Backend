package com.ds.backend.report.repository;

import com.ds.backend.report.entity.AnalysisReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<AnalysisReport, Long>, JpaSpecificationExecutor<AnalysisReport> {
    Optional<AnalysisReport> findByReportId(UUID reportId);
    Optional<AnalysisReport> findFirstByReportTypeOrderByGeneratedAtDesc(String reportType);
    Page<AnalysisReport> findByReportType(String reportType, Pageable pageable);
}
