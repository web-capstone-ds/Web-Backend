package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.BatchDetailResponse;
import com.ds.backend.analysis.dto.AiDtos.BatchEnvelope;
import com.ds.backend.analysis.dto.AiDtos.BatchListResponse;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryData;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.dto.QueryDtos.QueryRequest;
import com.ds.backend.analysis.dto.QueryDtos.QueryResponse;
import com.ds.backend.auth.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiServerClient {
    private final RestClient restClient;
    private final RestClient queryRestClient;
    private final JwtService jwtService;
    private final int retryMaxAttempts;
    private final boolean enabled;

    public AiServerClient(@Qualifier("aiRestClient") RestClient aiRestClient,
                          @Qualifier("aiQueryRestClient") RestClient aiQueryRestClient,
                          JwtService jwtService,
                          @Value("${ai-server.retry-max-attempts:2}") int retryMaxAttempts,
                          @Value("${ai-server.enabled:false}") boolean enabled) {
        this.restClient = aiRestClient;
        this.queryRestClient = aiQueryRestClient;
        this.jwtService = jwtService;
        this.retryMaxAttempts = retryMaxAttempts;
        this.enabled = enabled;
    }

    public Optional<KpiSummaryResponse> kpiSummary(Map<String, ?> queryParams) {
        return kpiSummaryData(queryParams).map(KpiSummaryData::summary);
    }

    public Optional<KpiSummaryData> kpiSummaryData(Map<String, ?> queryParams) {
        if (!enabled) {
            return Optional.empty();
        }
        return getEnvelopeWithRetry("/api/batches/kpi-summary", queryParams, new ParameterizedTypeReference<BatchEnvelope<KpiSummaryData>>() {});
    }

    public Optional<BatchListResponse> listBatches(Map<String, ?> queryParams) {
        if (!enabled) {
            return Optional.empty();
        }
        return getEnvelopeWithRetry("/api/batches", queryParams, new ParameterizedTypeReference<BatchEnvelope<BatchListResponse>>() {});
    }

    public Optional<BatchDetailResponse> getBatch(String batchId) {
        if (!enabled || batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }
        return getEnvelopeWithRetry("/api/batches/" + batchId, Map.of(), new ParameterizedTypeReference<BatchEnvelope<BatchDetailResponse>>() {});
    }

    public Optional<BatchDetailResponse> latestBatch(String equipmentId) {
        if (!enabled || equipmentId == null || equipmentId.isBlank() || "all".equalsIgnoreCase(equipmentId)) {
            return Optional.empty();
        }
        return getEnvelopeWithRetry("/api/batches/latest", Map.of("equipmentId", equipmentId), new ParameterizedTypeReference<BatchEnvelope<BatchDetailResponse>>() {});
    }

    public Optional<QueryResponse> query(QueryRequest request) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> body = postOnce("/api/query", request);
        return body.map(value -> new QueryResponse(
                stringValue(value.get("answer"), ""),
                listValue(value.get("sources")),
                doubleValue(value.get("confidence"), 0.0)
        ));
    }

    private <T> Optional<T> getEnvelopeWithRetry(String uri, Map<String, ?> queryParams, ParameterizedTypeReference<BatchEnvelope<T>> responseType) {
        return executeWithRetry(() -> restClient.get()
                .uri(builder -> {
                    var uriBuilder = builder.path(uri);
                    queryParams.forEach((key, value) -> {
                        if (value != null) {
                            uriBuilder.queryParam(key, value);
                        }
                    });
                    return uriBuilder.build();
                })
                .header("Authorization", "Bearer " + serviceToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new NonRetryableAiException("AI server rejected request: " + response.getStatusCode());
                })
                .body(responseType))
                .filter(BatchEnvelope::ok)
                .map(BatchEnvelope::data);
    }

    private Optional<Map<String, Object>> postOnce(String uri, Object body) {
        try {
            return Optional.ofNullable(queryRestClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + serviceToken())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new NonRetryableAiException("AI server rejected request: " + response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {}));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    private String serviceToken() {
        return jwtService.createServiceToken("web-backend");
    }

    private <T> Optional<T> executeWithRetry(AiCall<T> call) {
        int attempts = Math.max(retryMaxAttempts, 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return Optional.ofNullable(call.execute());
            } catch (NonRetryableAiException ex) {
                return Optional.empty();
            } catch (RestClientException ex) {
                if (attempt == attempts) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private double doubleValue(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    @FunctionalInterface
    private interface AiCall<T> {
        T execute();
    }

    private static class NonRetryableAiException extends RestClientException {
        NonRetryableAiException(String message) {
            super(message);
        }
    }
}
