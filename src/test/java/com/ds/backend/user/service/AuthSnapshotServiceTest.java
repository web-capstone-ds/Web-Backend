package com.ds.backend.user.service;

import com.ds.backend.user.dto.AuthSnapshotResponse;
import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

public class AuthSnapshotServiceTest {

    private UserRepository userRepository;
    private AuthSnapshotService authSnapshotService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        authSnapshotService = new AuthSnapshotService(userRepository, objectMapper);
    }

    @Test
    void sinceZeroShouldReturnAllUsers() {
        // Given
        User user1 = new User();
        user1.setOperatorId("op1");
        user1.setPasswordHash("hash1");
        user1.setRole(Role.OPERATOR);
        user1.setActive(true);
        user1.setVersion(1L);
        user1.setUpdatedAt(OffsetDateTime.now());

        when(userRepository.findByVersionGreaterThanOrderByVersionAsc(0L)).thenReturn(List.of(user1));
        when(userRepository.findMaxVersion()).thenReturn(Optional.of(1L));

        // When
        AuthSnapshotResponse response = authSnapshotService.getSnapshot(0L);

        // Then
        assertThat(response.users()).hasSize(1);
        assertThat(response.version()).isEqualTo(1L);
        assertThat(response.checksum()).isNotEmpty();
    }

    @Test
    void sinceHigherShouldReturnEmptyIfNoChanges() {
        // Given
        when(userRepository.findByVersionGreaterThanOrderByVersionAsc(42L)).thenReturn(List.of());
        when(userRepository.findMaxVersion()).thenReturn(Optional.of(42L));

        // When
        AuthSnapshotResponse response = authSnapshotService.getSnapshot(42L);

        // Then
        assertThat(response.users()).isEmpty();
        assertThat(response.version()).isEqualTo(42L);
        assertThat(response.checksum()).isNotEmpty(); // SHA-256 of []
    }

    @Test
    void checksumShouldMatchCalculatedHash() throws Exception {
        // Given
        User user1 = new User();
        user1.setOperatorId("op1");
        user1.setPasswordHash("hash1");
        user1.setRole(Role.OPERATOR);
        user1.setActive(true);
        user1.setVersion(1L);
        user1.setUpdatedAt(OffsetDateTime.now());

        when(userRepository.findByVersionGreaterThanOrderByVersionAsc(0L)).thenReturn(List.of(user1));
        when(userRepository.findMaxVersion()).thenReturn(Optional.of(1L));

        // When
        AuthSnapshotResponse response = authSnapshotService.getSnapshot(0L);

        // Then
        byte[] expectedBytes = objectMapper.writeValueAsBytes(response.users());
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] expectedHash = digest.digest(expectedBytes);
        String expectedChecksum = java.util.HexFormat.of().withLowerCase().formatHex(expectedHash);

        assertThat(response.checksum()).isEqualTo(expectedChecksum);
    }
}
