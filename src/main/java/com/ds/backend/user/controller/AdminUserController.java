package com.ds.backend.user.controller;

import com.ds.backend.common.dto.ApiResponse;
import com.ds.backend.user.dto.UserCreateRequest;
import com.ds.backend.user.dto.UserDto;
import com.ds.backend.user.dto.UserUpdateRequest;
import com.ds.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDto> updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deactivateUser(@PathVariable String id) {
        userService.deactivateUser(id);
        return ApiResponse.ok("User deactivated");
    }
}
