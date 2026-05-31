package com.ds.backend.auth.service;

import com.ds.backend.auth.dto.LoginRequest;
import com.ds.backend.auth.dto.RefreshRequest;
import com.ds.backend.auth.dto.TokenResponse;
import com.ds.backend.audit.service.AuditService;
import com.ds.backend.common.exception.BusinessException;
import com.ds.backend.user.dto.UserDto;
import com.ds.backend.user.entity.User;
import com.ds.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, TokenHashService tokenHashService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findById(request.operatorId())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        JwtService.Claims claims = jwtService.parse(request.refreshToken())
                .filter(parsed -> "refresh".equals(parsed.type()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        RefreshToken token = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHashService.hash(request.refreshToken()))
                .filter(saved -> saved.getExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(saved -> saved.getUser().isActive())
                .filter(saved -> saved.getUser().getOperatorId().equals(claims.userId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        token.setRevoked(true);
        return issue(token.getUser());
    }

    @Transactional
    public void logout(JwtService.Claims claims, RefreshRequest request) {
        JwtService.Claims refreshClaims = jwtService.parse(request.refreshToken())
                .filter(parsed -> "refresh".equals(parsed.type()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!claims.userId().equals(refreshClaims.userId())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHashService.hash(request.refreshToken()))
                .filter(saved -> saved.getExpiresAt().isAfter(OffsetDateTime.now()))
                .filter(saved -> saved.getUser().isActive())
                .filter(saved -> saved.getUser().getOperatorId().equals(claims.userId()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        token.setRevoked(true);
    }

    public UserDto me(JwtService.Claims claims) {
        return userRepository.findById(claims.userId())
                .filter(User::isActive)
                .map(UserDto::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private TokenResponse issue(User user) {
        String access = jwtService.createAccessToken(user.getOperatorId(), user.getOperatorId(), user.getRole());
        String refresh = jwtService.createRefreshToken(user.getOperatorId(), user.getOperatorId(), user.getRole());
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHashService.hash(refresh));
        token.setExpiresAt(OffsetDateTime.now().plusDays(7));
        refreshTokenRepository.save(token);
        return new TokenResponse(access, refresh, "Bearer", 1800, UserDto.from(user));
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }
}
