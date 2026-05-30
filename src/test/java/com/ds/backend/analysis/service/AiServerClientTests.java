package com.ds.backend.analysis.service;

import com.ds.backend.analysis.dto.AiDtos.KpiSummaryData;
import com.ds.backend.analysis.dto.QueryDtos.QueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServerClientTests {
    @Test
    void disabledClientDoesNotCallAiServer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        AiServerClient client = new AiServerClient(restClient, restClient, "service-token", 2, false);

        assertThat(client.kpiSummaryData(Map.of())).isEmpty();
        server.verify();
    }

    @Test
    void clientErrorsAreNotRetried() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://ai.test/api/batches/kpi-summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withBadRequest());
        RestClient restClient = builder.build();
        AiServerClient client = new AiServerClient(restClient, restClient, "service-token", 2, true);

        assertThat(client.kpiSummaryData(Map.of())).isEmpty();
        server.verify();
    }

    @Test
    void serverErrorsAreRetriedUntilSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicInteger attempts = new AtomicInteger();
        server.expect(times(2), requestTo("http://ai.test/api/batches/kpi-summary?equipmentId=SAW-EQ.01"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(request -> {
                    if (attempts.incrementAndGet() == 1) {
                        return withServerError().createResponse(request);
                    }
                    return withSuccess("""
                            {
                              "status": "ok",
                              "data": {
                                "summary": { "totalUnits": 1265, "totalInspected": 5, "totalFail": 1 },
                                "groups": []
                              }
                            }
                            """, MediaType.APPLICATION_JSON).createResponse(request);
                });
        RestClient restClient = builder.build();
        AiServerClient client = new AiServerClient(restClient, restClient, "service-token", 2, true);

        assertThat(client.kpiSummaryData(Map.of("equipmentId", "SAW-EQ.01")))
                .hasValueSatisfying(data -> assertThat(data.summary().totalUnits()).isEqualTo(1265));
        server.verify();
    }

    @Test
    void queryPostIsNotRetried() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        server.expect(once(), requestTo("http://ai.test/api/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withServerError());
        AiServerClient client = new AiServerClient(restClient, restClient, "service-token", 2, true);

        assertThat(client.query(new QueryRequest("최근 치핑 추세를 분석해줘", null))).isEmpty();
        server.verify();
    }
}
