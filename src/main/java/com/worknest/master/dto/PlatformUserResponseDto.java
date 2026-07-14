package com.worknest.master.dto;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;

import java.time.LocalDateTime;

public record PlatformUserResponseDto(
        Long id,
        String fullName,
        String email,
        PlatformRole role,
        UserStatus status,
        String tenantKey,
        String companyName,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        long activeSessions) {
}
