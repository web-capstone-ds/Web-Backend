package com.ds.backend.user.service;

import com.ds.backend.user.dto.AuthSnapshotResponse;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuthSnapshotService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuthSnapshotService(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuthSnapshotResponse getSnapshot(Long since) {
        List<User> users = userRepository.findByVersionGreaterThanOrderByVersionAsc(since);
        Long maxVersion = userRepository.findMaxVersion().orElse(0L);

        List<AuthSnapshotResponse.SnapshotUserDto> userDtos = users.stream()
                .map(u -> new AuthSnapshotResponse.SnapshotUserDto(
                        u.getOperatorId(),
                        u.getPasswordHash(),
                        u.getRole().name(),
                        u.isActive(),
                        u.getUpdatedAt()
                ))
                .toList();

        String checksum = calculateChecksum(userDtos);

        return new AuthSnapshotResponse(maxVersion, userDtos, checksum);
    }

    private String calculateChecksum(List<AuthSnapshotResponse.SnapshotUserDto> users) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(users);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonBytes);
            return HexFormat.of().withLowerCase().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }
}
