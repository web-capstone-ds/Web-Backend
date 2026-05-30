package com.ds.backend.user.controller;

import com.ds.backend.auth.service.JwtService;
import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.user.dto.UserDto;
import com.ds.backend.user.dto.UserUpdateRequest;
import com.ds.backend.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> getMe(@AuthenticationPrincipal JwtService.Claims claims) {
        return ApiResponse.ok(userService.getMe(claims.userId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserDto> updateMe(@AuthenticationPrincipal JwtService.Claims claims, @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateUser(claims.userId(), request));
    }
}
