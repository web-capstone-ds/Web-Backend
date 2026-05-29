package com.ds.backend.user.dto;

import com.ds.backend.user.entity.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequest(
    @NotBlank String operatorId,
    @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
    @NotNull Role role
) {
    @Override
    public String toString() {
        return "UserCreateRequest[operatorId=" + operatorId + ", password=[PROTECTED], role=" + role + "]";
    }
}
