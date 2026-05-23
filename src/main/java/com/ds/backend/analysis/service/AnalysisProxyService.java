package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.QueryDtos.*;
import com.ds.backend.analysis.entity.QueryHistory;
import com.ds.backend.analysis.repository.QueryHistoryRepository;
import com.ds.backend.audit.service.AuditService;
import com.ds.backend.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisProxyService {
    private final QueryHistoryRepository queryHistoryRepository;
    private final ObjectMapper objectMapper;
    private final AiServerClient aiServerClient;
    private final AuditService auditService;

    public AnalysisProxyService(QueryHistoryRepository queryHistoryRepository, ObjectMapper objectMapper,
                                AiServerClient aiServerClient, AuditService auditService) {
        this.queryHistoryRepository = queryHistoryRepository;
        this.objectMapper = objectMapper;
        this.aiServerClient = aiServerClient;
        this.auditService = auditService;
    }

    @Transactional
    public QueryResponse query(JwtService.Claims claims, QueryRequest request) {
        long start = System.currentTimeMillis();
        QueryResponse response = aiServerClient.query(request)
                .orElseGet(() -> new QueryResponse("AI 서버 응답을 받을 수 없어 캐시된 기본 응답을 반환합니다. 질의: " + request.question(), List.of("fallback"), 0.0));
        QueryHistory history = new QueryHistory();
        history.setUserId(claims.userId());
        history.setQuestion(request.question());
        history.setAnswer(response.answer());
        history.setConfidence(response.confidence());
        history.setResponseTimeMs((int) (System.currentTimeMillis() - start));
        try {
            history.setFilters(objectMapper.writeValueAsString(request.filters() == null ? java.util.Map.of() : request.filters()));
            history.setSources(objectMapper.writeValueAsString(response.sources()));
        } catch (Exception ignored) {
            history.setFilters("{}");
            history.setSources("[]");
        }
        QueryHistory saved = queryHistoryRepository.save(history);
        auditService.record(claims.userId(), "QUERY", "query", saved.getId().toString());
        return response;
    }

    public Page<QueryHistoryResponse> history(JwtService.Claims claims, Pageable pageable) {
        return queryHistoryRepository.findByUserIdOrderByCreatedAtDesc(claims.userId(), pageable).map(QueryHistoryResponse::from);
    }
}
