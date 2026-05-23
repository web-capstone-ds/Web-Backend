package com.ds.backend.review.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_reviews")
public class ReportReview {
    @Id
    @Column(name = "review_id")
    private UUID reviewId = UUID.randomUUID();
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;
    @Column(name = "reviewer_role", nullable = false)
    private String reviewerRole;
    @Column(name = "reviewer_name", nullable = false)
    private String reviewerName;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getReviewId() { return reviewId; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getReviewerRole() { return reviewerRole; }
    public void setReviewerRole(String reviewerRole) { this.reviewerRole = reviewerRole; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
