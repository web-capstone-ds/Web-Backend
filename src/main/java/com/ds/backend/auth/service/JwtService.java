package com.ds.backend.auth.service;

import com.ds.backend.user.entity.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-token-expiry-ms:1800000}") long accessTtlMs,
                      @Value("${jwt.refresh-token-expiry-ms:604800000}") long refreshTtlMs) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    @PostConstruct
    void validateSecret() {
        if (secret.length < 64) {
            throw new IllegalStateException("JWT secret must be at least 64 bytes");
        }
    }

    public String createAccessToken(Long userId, String email, Role role) {
        return createToken(userId, email, role, accessTtlMs, "access");
    }

    public String createRefreshToken(Long userId, String email, Role role) {
        return createToken(userId, email, role, refreshTtlMs, "refresh");
    }

    public Optional<Claims> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !sign(parts[0] + "." + parts[1]).equals(parts[2])) {
                return Optional.empty();
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                    Long.valueOf(payload.get("sub").toString()),
                    payload.get("email").toString(),
                    Role.valueOf(payload.get("role").toString()),
                    payload.get("type").toString()
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String createToken(Long userId, String email, Role role, long ttlMs, String type) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", userId,
                    "email", email,
                    "role", role.name(),
                    "type", type,
                    "iat", Instant.now().getEpochSecond(),
                    "jti", UUID.randomUUID().toString(),
                    "exp", Instant.now().plusMillis(ttlMs).getEpochSecond()
            )));
            String body = header + "." + payload;
            return body + "." + sign(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create JWT", ex);
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return encode(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Claims(Long userId, String email, Role role, String type) {}
}
