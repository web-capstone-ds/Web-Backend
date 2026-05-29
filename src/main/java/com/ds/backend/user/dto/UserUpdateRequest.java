package com.ds.backend.user.dto;

import com.ds.backend.user.entity.Role;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserUpdateRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
    Role role,
    Boolean active
) {
    @Override
    public String toString() {
        return "UserUpdateRequest[password=[PROTECTED], role=" + role + ", active=" + active + "]";
    }
}
