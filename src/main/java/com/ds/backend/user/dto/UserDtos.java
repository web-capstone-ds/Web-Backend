package com.ds.backend.user.dto;

import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class UserDtos {
    private UserDtos() {}

    public record UserCreateRequest(@Email String email, @NotBlank String password, @NotBlank String name, Role role, String department) {}
    public record UserUpdateRequest(String name, String password, String department) {}
    public record RoleUpdateRequest(Role role) {}
    public record UserResponse(Long id, String email, String name, Role role, String department, boolean active) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getDepartment(), user.isActive());
        }
    }
}
