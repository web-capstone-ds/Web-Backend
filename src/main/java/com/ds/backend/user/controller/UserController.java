package com.ds.backend.user.controller;

import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.common.dto.PageResponse;
import com.ds.backend.user.dto.UserDtos.*;
import com.ds.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Boolean active,
                                                        @RequestParam(required = false) com.ds.backend.user.entity.Role role,
                                                        @RequestParam(required = false) String search) {
        return ApiResponse.ok(PageResponse.from(userService.list(active, role, search, PageRequest.of(Math.max(page - 1, 0), size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@AuthenticationPrincipal JwtService.Claims claims,
                                                            @Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(userService.create(claims.userId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal JwtService.Claims claims,
                                            @PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.update(claims.userId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> delete(@PathVariable Long id) {
        userService.deactivate(id);
        return ApiResponse.ok("deactivated");
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> changeRole(@AuthenticationPrincipal JwtService.Claims claims,
                                                @PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok(userService.changeRole(claims.userId(), id, request));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal JwtService.Claims claims, @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.update(claims.userId(), claims.userId(), request));
    }
}
