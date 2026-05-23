package com.ds.backend.review.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_comments")
public class ReportComment {
    @Id
    @Column(name = "comment_id")
    private UUID commentId = UUID.randomUUID();
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;
    @Column(nullable = false)
    private String author;
    @Column(nullable = false, length = 4000)
    private String content;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getCommentId() { return commentId; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
