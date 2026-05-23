package com.ds.backend.analysis.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;

@Entity
@Table(name = "query_history")
public class QueryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 4000)
    private String question;
    @Column(length = 4000)
    private String answer;
    @ColumnTransformer(write = "CAST(? AS JSON)")
    @Column(columnDefinition = "jsonb")
    private String filters;
    @ColumnTransformer(write = "CAST(? AS JSON)")
    @Column(columnDefinition = "jsonb")
    private String sources;
    private Double confidence;
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }
    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
