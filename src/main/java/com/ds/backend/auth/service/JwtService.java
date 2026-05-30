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
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String rsaPrivateKeyPem;
    private PrivateKey rsaPrivateKey;
    private final String aiServerPublicKeyPem;
    private PublicKey aiServerPublicKey;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${jwt.secret}") String secret,
                      @Value("${jwt.rsa-private-key:}") String rsaPrivateKeyPem,
                      @Value("${ai-server.public-key:}") String aiServerPublicKeyPem,
                      @Value("${jwt.access-token-expiry-ms:1800000}") long accessTtlMs,
                      @Value("${jwt.refresh-token-expiry-ms:604800000}") long refreshTtlMs) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.rsaPrivateKeyPem = rsaPrivateKeyPem;
        this.aiServerPublicKeyPem = aiServerPublicKeyPem;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    @PostConstruct
    void validateSecret() {
        if (secret.length < 64) {
            throw new IllegalStateException("JWT secret must be at least 64 bytes");
        }
        if (rsaPrivateKeyPem != null && !rsaPrivateKeyPem.isBlank()) {
            rsaPrivateKey = loadPrivateKey(rsaPrivateKeyPem);
        }
        if (aiServerPublicKeyPem != null && !aiServerPublicKeyPem.isBlank()) {
            aiServerPublicKey = loadPublicKey(aiServerPublicKeyPem);
        }
    }

    public String createAccessToken(String userId, String email, Role role) {
        return createToken(userId, email, role, accessTtlMs, "access");
    }

    public String createRefreshToken(String userId, String email, Role role) {
        return createToken(userId, email, role, refreshTtlMs, "refresh");
    }

    public String createServiceToken(String serviceId) {
        if (rsaPrivateKey == null) {
            throw new IllegalStateException("JWT RSA private key is not configured");
        }
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", serviceId,
                    "type", "service",
                    "iat", Instant.now().getEpochSecond(),
                    "jti", UUID.randomUUID().toString(),
                    "exp", Instant.now().plusSeconds(300).getEpochSecond()
            )));
            String body = header + "." + payload;
            return body + "." + signRs256(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create service JWT", ex);
        }
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
                    payload.get("sub").toString(),
                    payload.get("email").toString(),
                    Role.valueOf(payload.get("role").toString()),
                    payload.get("type").toString()
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String createToken(String userId, String email, Role role, long ttlMs, String type) {
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

    private String signRs256(String body) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(rsaPrivateKey);
        signature.update(body.getBytes(StandardCharsets.UTF_8));
        return encode(signature.sign());
    }

    public boolean parseServiceToken(String token) {
        if (aiServerPublicKey == null) return false;
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(aiServerPublicKey);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) return false;
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            return Instant.now().getEpochSecond() <= exp;
        } catch (Exception ex) {
            return false;
        }
    }

    private PublicKey loadPublicKey(String pem) {
        try {
            String normalized = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid AI Server RSA public key", ex);
        }
    }

    private PrivateKey loadPrivateKey(String pem) {
        try {
            String normalized = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid JWT RSA private key", ex);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Claims(String userId, String email, Role role, String type) {}
}
