package com.ds.backend.auth.dto;

import com.ds.backend.user.dto.UserDto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds, UserDto user) {}
