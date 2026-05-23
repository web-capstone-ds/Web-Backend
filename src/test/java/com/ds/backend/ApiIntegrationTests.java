package com.ds.backend;

import com.ds.backend.analysis.dto.AiDtos.BatchEnvelope;
import com.ds.backend.analysis.dto.AiDtos.KpiSummaryData;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.audit.repository.AuditLogRepository;
import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Value("${ai-server.service-token}")
    String serviceToken;

    @Test
    void loginIssuesJwtTokens() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        createUser(email, "plain-password", Role.ENGINEER);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "plain-password"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("LOGIN")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void badPasswordLoginReturnsUnauthorizedEnvelope() throws Exception {
        String email = "bad-password-" + UUID.randomUUID() + "@example.test";
        createUser(email, "plain-password", Role.ENGINEER);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.message", notNullValue()));
    }

    @Test
    void softDeletedUserCannotLogin() throws Exception {
        String email = "deleted-login-" + UUID.randomUUID() + "@example.test";
        User user = createUser(email, "plain-password", Role.ENGINEER);
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "plain-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@example.test";
        createUser(email, "plain-password", Role.ENGINEER);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "plain-password"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(loginBody).path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("LOGOUT")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void logoutRequiresAuthenticationAndMatchingRefreshTokenOwner() throws Exception {
        User first = createUser("logout-first-" + UUID.randomUUID() + "@example.test", "plain-password", Role.ENGINEER);
        User second = createUser("logout-second-" + UUID.randomUUID() + "@example.test", "plain-password", Role.ENGINEER);
        String firstAccessToken = jwtService.createAccessToken(first.getId(), first.getEmail(), first.getRole());

        String secondLoginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", second.getEmail(),
                                "password", "plain-password"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondRefreshToken = objectMapper.readTree(secondLoginBody).path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefreshToken))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + firstAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void operatorCanReadDashboardButCannotCreateAction() throws Exception {
        User operator = createUser("operator-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpi.availability").value(87.3))
                .andExpect(jsonPath("$.data.kpi.totalDowntimeMin").value(257))
                .andExpect(jsonPath("$.data.kpi.oee").doesNotExist())
                .andExpect(jsonPath("$.data.kpi.productionRate").doesNotExist());

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "actionType", "블레이드 교체",
                                "performedBy", "테스트 오퍼레이터"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerCanCreateAction() throws Exception {
        User engineer = createUser("engineer-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "alarmId", "A-TEST",
                                "actionType", "렌즈 세정",
                                "performedBy", "테스트 엔지니어",
                                "resultBefore", 92.1,
                                "resultAfter", 98.5,
                                "note", "integration test"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.actionStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.equipmentId").value("SAW-EQ.01"))
                .andExpect(jsonPath("$.data.performedBy").value(engineer.getEmail()));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("CREATE_ACTION")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void pendingActionsReturnsEmptyUntilAlarmHistoryJoinExists() throws Exception {
        User operator = createUser("pending-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/actions/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void actionStatusTransitionRejectsUnknownStatus() throws Exception {
        User engineer = createUser("status-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        String body = mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "actionType", "렌즈 세정",
                                "performedBy", "테스트 엔지니어"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/actions/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionStatus", "BROKEN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actionStatusRejectsReverseTransition() throws Exception {
        User engineer = createUser("reverse-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        String body = mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "actionType", "렌즈 세정",
                                "performedBy", "테스트 엔지니어"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/actions/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionStatus", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/actions/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionStatus", "PENDING"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actionStatusRejectsSkippingPendingToCompleted() throws Exception {
        User engineer = createUser("skip-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        String body = mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "actionType", "렌즈 세정",
                                "performedBy", "ignored"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/actions/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actionStatus", "COMPLETED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanManageUsersAndOperatorCannot() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", "password", Role.ADMIN);
        User operator = createUser("user-operator-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String adminToken = jwtService.createAccessToken(admin.getId(), admin.getEmail(), admin.getRole());
        String operatorToken = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        String email = "managed-" + UUID.randomUUID() + "@example.test";
        String createBody = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "managed-password",
                                "name", "관리 대상",
                                "role", "OPERATOR",
                                "department", "QA"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(createBody).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/users/{id}", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "수정된 사용자"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 사용자"));
        mockMvc.perform(patch("/api/v1/users/{id}/role", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ENGINEER"));
        mockMvc.perform(delete("/api/v1/users/{id}", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "blocked-" + UUID.randomUUID() + "@example.test",
                                "password", "blocked-password",
                                "name", "blocked"
                        ))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/{id}", operator.getId())
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "blocked"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{id}/role", operator.getId())
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/{id}", operator.getId()).header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("CREATE_USER")).isGreaterThanOrEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("UPDATE_USER")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void userCanChangeOwnPassword() throws Exception {
        String email = "me-" + UUID.randomUUID() + "@example.test";
        User engineer = createUser(email, "old-password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "new-password"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "new-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenCannotBeReused() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.test";
        createUser(email, "plain-password", Role.ENGINEER);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "plain-password"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reportListSupportsTypeDateAndSortFilters() throws Exception {
        User operator = createUser("report-filter-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        postReport("daily", OffsetDateTime.parse("2026-05-20T00:00:00Z"), OffsetDateTime.parse("2026-05-20T23:59:00Z"));
        postReport("weekly", OffsetDateTime.parse("2026-05-13T00:00:00Z"), OffsetDateTime.parse("2026-05-20T23:59:00Z"));

        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "daily")
                        .param("from", "2026-05-19T00:00:00Z")
                        .param("to", "2026-05-21T00:00:00Z")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reportType").value("daily"));
    }

    @Test
    void onlyCommentAuthorOrAdminCanDeleteComment() throws Exception {
        User author = createUser("author-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        User other = createUser("other-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String authorToken = jwtService.createAccessToken(author.getId(), author.getEmail(), author.getRole());
        String otherToken = jwtService.createAccessToken(other.getId(), other.getEmail(), other.getRole());

        String body = mockMvc.perform(post("/api/v1/reports/2026-05-22/comments")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "확인 필요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author").value(author.getEmail()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/reports/2026-05-22/comments/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/reports/2026-05-22/comments/{id}", id)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
    }

    @Test
    void reportReceiveRequiresServiceToken() throws Exception {
        Map<String, Object> report = Map.of(
                "reportId", UUID.randomUUID().toString(),
                "reportType", "daily",
                "periodStart", OffsetDateTime.now().minusDays(1).toString(),
                "periodEnd", OffsetDateTime.now().toString(),
                "summary", "integration report",
                "generatedAt", OffsetDateTime.now().toString(),
                "content", Map.of("yield", 98.7)
        );

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId", notNullValue()))
                .andExpect(jsonPath("$.data.status").value("stored"));
    }

    @Test
    void duplicateReportUpdatesAndDownloadRequiresEngineerOrAdmin() throws Exception {
        UUID reportId = UUID.randomUUID();
        Map<String, Object> report = Map.of(
                "reportId", reportId.toString(),
                "reportType", "daily",
                "periodStart", OffsetDateTime.now().minusDays(1).toString(),
                "periodEnd", OffsetDateTime.now().toString(),
                "summary", "first",
                "generatedAt", OffsetDateTime.now().toString(),
                "content", Map.of("yield", 98.7)
        );

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("stored"));
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("updated"));

        User engineer = createUser("download-engineer-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        User operator = createUser("download-operator-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String engineerToken = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());
        String operatorToken = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/reports/{id}/download", reportId)
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("daily-" + reportId + ".json")));
        mockMvc.perform(get("/api/v1/reports/{id}/download", reportId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidReviewRoleReturnsBadRequest() throws Exception {
        User engineer = createUser("review-role-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        mockMvc.perform(post("/api/v1/reports/2026-05-22/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewerRole", "approver",
                                "reviewerName", "bad"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestIdHeaderIsGeneratedOrEchoedAndSwaggerDocsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));

        mockMvc.perform(get("/actuator/health").header("X-Request-ID", "req-test-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-test-123"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void reportDataEquipmentModeRequiresEquipmentIdAndAlarmsAreEmptyUntilJoinExists() throws Exception {
        User operator = createUser("report-mode-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("reportMode", "equipment"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/reports/alarms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void auditLogCapturesUserAndRequestMetadata() throws Exception {
        User engineer = createUser("audit-meta-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        mockMvc.perform(post("/api/v1/actions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header("User-Agent", "MockMvc-Audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "equipmentId", "SAW-EQ.01",
                                "actionType", "렌즈 세정"
                        ))))
                .andExpect(status().isCreated());

        com.ds.backend.audit.entity.AuditLog log = auditLogRepository.findAll().stream()
                .filter(item -> "CREATE_ACTION".equals(item.getAction()) && engineer.getId().equals(item.getUserId()))
                .reduce((first, second) -> second)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(log.getIpAddress()).isEqualTo("203.0.113.10");
        org.assertj.core.api.Assertions.assertThat(log.getUserAgent()).isEqualTo("MockMvc-Audit");
    }

    @Test
    void recipeSpecsAreUsedByEquipmentAndReportApis() throws Exception {
        User operator = createUser("recipe-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/equipments/SAW-EQ.01/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.info.recipe").value("Carsem_3X3"))
                .andExpect(jsonPath("$.data.parameters[0].usl").value(12.04));

        mockMvc.perform(get("/api/v1/reports/quality-distribution")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distributionChart.guidelines.lsl").value(11.96))
                .andExpect(jsonPath("$.data.distributionChart.guidelines.usl").value(12.04));
    }

    @Test
    void aiKpiSummaryEnvelopeCanBeParsed() throws Exception {
        String json = """
                {
                  "status": "ok",
                  "requestId": "req-test",
                  "servedAt": "2026-05-23T03:14:00.000Z",
                  "data": {
                    "period": { "from": "2026-05-22T03:00:00.000Z", "to": "2026-05-23T03:00:00.000Z" },
                    "summary": {
                      "totalUnits": 1265,
                      "totalInspected": 5,
                      "totalFail": 4,
                      "avgYieldPct": 0.95,
                      "avgUph": 1200.0,
                      "avgAvailabilityPct": 75.0,
                      "totalDowntimeMin": 30.0,
                      "activeEquipmentCount": 1,
                      "totalEquipmentCount": 1,
                      "avgMtbfHours": 12.0,
                      "topFailReasons": [
                        { "fail_reason_code": "ET=52", "count": 4 }
                      ]
                    },
                    "groups": [
                      { "key": "DS-VIS-001", "totalUnits": 1265, "avgYieldPct": 0.95 }
                    ]
                  },
                  "error": null
                }
                """;

        BatchEnvelope<KpiSummaryData> parsed = objectMapper.readValue(json, new TypeReference<>() {});

        org.assertj.core.api.Assertions.assertThat(parsed.ok()).isTrue();
        org.assertj.core.api.Assertions.assertThat(parsed.data().summary().totalUnits()).isEqualTo(1265);
        org.assertj.core.api.Assertions.assertThat(parsed.data().summary().topFailReasons().get(0).displayCode()).isEqualTo("ET=52");
        org.assertj.core.api.Assertions.assertThat(parsed.data().groups().get(0).displayName()).isEqualTo("DS-VIS-001");
    }

    @Test
    void dashboardRejectsMonthlyTrendUnit() throws Exception {
        User operator = createUser("monthly-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/dashboard/trend")
                        .header("Authorization", "Bearer " + token)
                        .param("unit", "monthly"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reportHeatmapRequiresEquipmentModeAndEquipmentId() throws Exception {
        User operator = createUser("heatmap-" + UUID.randomUUID() + "@example.test", "password", Role.OPERATOR);
        String token = jwtService.createAccessToken(operator.getId(), operator.getEmail(), operator.getRole());

        mockMvc.perform(get("/api/v1/reports/heatmap")
                        .header("Authorization", "Bearer " + token)
                        .param("reportMode", "daily"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/reports/heatmap")
                        .header("Authorization", "Bearer " + token)
                        .param("reportMode", "equipment"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ragQueryStoresHistoryAndAuditEvenWhenAiServerDisabled() throws Exception {
        User engineer = createUser("query-" + UUID.randomUUID() + "@example.test", "password", Role.ENGINEER);
        String token = jwtService.createAccessToken(engineer.getId(), engineer.getEmail(), engineer.getRole());

        mockMvc.perform(post("/api/v1/analysis/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "금일 수율 이상 원인은?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer", notNullValue()))
                .andExpect(jsonPath("$.data.sources[0]").value("fallback"));

        mockMvc.perform(get("/api/v1/analysis/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.countByAction("QUERY")).isGreaterThanOrEqualTo(1);
    }

    private User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName("테스트 사용자");
        user.setRole(role);
        user.setDepartment("QA");
        return userRepository.save(user);
    }

    private void postReport(String type, OffsetDateTime periodStart, OffsetDateTime periodEnd) throws Exception {
        Map<String, Object> report = Map.of(
                "reportId", UUID.randomUUID().toString(),
                "reportType", type,
                "periodStart", periodStart.toString(),
                "periodEnd", periodEnd.toString(),
                "summary", type + " report",
                "generatedAt", periodEnd.toString(),
                "content", Map.of("yield", 98.7)
        );
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report)))
                .andExpect(status().isCreated());
    }
}
