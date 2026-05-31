package com.ds.backend.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AuthSnapshotResponse(
    Long version,
    List<SnapshotUserDto> users,
    String checksum
) {
    public record SnapshotUserDto(
        String operatorId,
        String passwordHash,
        String name,
        String department,
        String phone,
        String role,
        boolean active,
        OffsetDateTime updatedAt
    ) {}
}
