package com.ds.backend.auth.controller;

import com.ds.backend.auth.dto.LoginRequest;
import com.ds.backend.auth.dto.RefreshRequest;
import com.ds.backend.auth.service.AuthService;
import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.user.dto.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<?> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@AuthenticationPrincipal JwtService.Claims claims, @Valid @RequestBody RefreshRequest request) {
        authService.logout(claims, request);
        return ApiResponse.ok("logged_out");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal JwtService.Claims claims) {
        return ApiResponse.ok(authService.me(claims));
    }
}
