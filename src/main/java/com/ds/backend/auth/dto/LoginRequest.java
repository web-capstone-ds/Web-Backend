package com.ds.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String operatorId, @NotBlank String password) {}
