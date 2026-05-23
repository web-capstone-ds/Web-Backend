package com.ds.backend.analysis.dto;

import com.ds.backend.analysis.entity.QueryHistory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class QueryDtos {
    private QueryDtos() {}
    public record QueryRequest(String question, Map<String, Object> filters) {}
    public record QueryResponse(String answer, List<String> sources, double confidence) {}
    public record QueryHistoryResponse(Long id, String question, String answer, Double confidence, OffsetDateTime createdAt) {
        public static QueryHistoryResponse from(QueryHistory history) {
            return new QueryHistoryResponse(history.getId(), history.getQuestion(), history.getAnswer(), history.getConfidence(), history.getCreatedAt());
        }
    }
}
