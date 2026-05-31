package com.ds.backend.user.dto;

import com.ds.backend.user.entity.Role;
import com.ds.backend.user.entity.User;
import java.time.OffsetDateTime;

public record UserDto(
    String operatorId,
    String name,
    String department,
    String phone,
    Role role,
    boolean active,
    OffsetDateTime updatedAt,
    Long version
) {
    public static UserDto from(User user) {
        return new UserDto(
            user.getOperatorId(),
            user.getName(),
            user.getDepartment(),
            user.getPhone(),
            user.getRole(),
            user.isActive(),
            user.getUpdatedAt(),
            user.getVersion()
        );
    }
}
