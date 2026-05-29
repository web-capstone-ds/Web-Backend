package com.ds.backend.user;

import com.ds.backend.user.controller.AuthSnapshotController;
import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import com.ds.backend.user.service.AuthSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.security.cert.X509Certificate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthSnapshotApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldReturnSnapshotWithValidCert() throws Exception {
        // Given: 유저 생성
        User user = new User();
        user.setOperatorId("tester-1");
        user.setPasswordHash("$2a$12$R9h/lIPzHZ7.3mDtLBaG3e9P6DqXzN9P6DqXzN9P6DqXzN9P6DqXz"); // test
        user.setRole(Role.OPERATOR);
        user.setActive(true);
        userRepository.saveAndFlush(user);

        // mTLS 인증서 모킹
        X509Certificate mockCert = mock(X509Certificate.class);
        javax.security.auth.x500.X500Principal principal = new javax.security.auth.x500.X500Principal("CN=dispatcher");
        when(mockCert.getSubjectX500Principal()).thenReturn(principal);
        X509Certificate[] certs = {mockCert};

        // When & Then
        mockMvc.perform(get("/api/auth/snapshot")
                .requestAttr("jakarta.servlet.request.X509Certificate", certs)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].operatorId").value("tester-1"))
                .andExpect(jsonPath("$.checksum").exists());
    }

    @Test
    void shouldReturn403WhenCertIsMissing() throws Exception {
        mockMvc.perform(get("/api/auth/snapshot")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
