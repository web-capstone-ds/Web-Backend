package com.ds.backend.review.repository;

import com.ds.backend.review.entity.ReportReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReportReviewRepository extends JpaRepository<ReportReview, UUID> {
    List<ReportReview> findByReportDate(LocalDate reportDate);
}
