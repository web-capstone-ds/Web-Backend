package com.ds.backend;

import com.ds.backend.analysis.entity.QueryHistory;
import com.ds.backend.analysis.repository.QueryHistoryRepository;
import com.ds.backend.audit.entity.AuditLog;
import com.ds.backend.audit.repository.AuditLogRepository;
import com.ds.backend.report.entity.AnalysisReport;
import com.ds.backend.report.repository.ReportRepository;
import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.timezone.default_storage=NORMALIZE"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgresJsonbIntegrationTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ds_backend_test")
            .withUsername("backend")
            .withPassword("backend");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    Flyway flyway;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    QueryHistoryRepository queryHistoryRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void flywayMigrationsValidateAndJsonbFieldsStoreAndRead() {
        flyway.validate();

        User user = new User();
        user.setEmail("jsonb-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("hash");
        user.setName("jsonb user");
        user.setRole(Role.ENGINEER);
        user = userRepository.saveAndFlush(user);

        AnalysisReport report = new AnalysisReport();
        report.setReportId(UUID.randomUUID());
        report.setReportType("daily");
        report.setPeriodStart(OffsetDateTime.now().minusDays(1));
        report.setPeriodEnd(OffsetDateTime.now());
        report.setSummary("jsonb test");
        report.setContent("{\"yield\":98.7,\"nested\":{\"ok\":true}}");
        report.setGeneratedAt(OffsetDateTime.now());

        AnalysisReport saved = reportRepository.saveAndFlush(report);

        org.assertj.core.api.Assertions.assertThat(reportRepository.findByReportId(saved.getReportId()))
                .hasValueSatisfying(found -> org.assertj.core.api.Assertions.assertThat(found.getContent()).contains("\"yield\""));

        QueryHistory history = new QueryHistory();
        history.setUserId(user.getId());
        history.setQuestion("jsonb filters?");
        history.setFilters("{\"equipmentId\":\"SAW-EQ.01\"}");
        history.setSources("[\"batch-1\"]");
        QueryHistory savedHistory = queryHistoryRepository.saveAndFlush(history);
        org.assertj.core.api.Assertions.assertThat(queryHistoryRepository.findById(savedHistory.getId()))
                .hasValueSatisfying(found -> {
                    org.assertj.core.api.Assertions.assertThat(found.getFilters()).contains("SAW-EQ.01");
                    org.assertj.core.api.Assertions.assertThat(found.getSources()).contains("batch-1");
                });

        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setAction("QUERY");
        log.setResourceType("query");
        log.setResourceId(savedHistory.getId().toString());
        log.setDetails("{\"source\":\"test\"}");
        AuditLog savedLog = auditLogRepository.saveAndFlush(log);
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findById(savedLog.getId()))
                .hasValueSatisfying(found -> org.assertj.core.api.Assertions.assertThat(found.getDetails()).contains("test"));
    }
}
