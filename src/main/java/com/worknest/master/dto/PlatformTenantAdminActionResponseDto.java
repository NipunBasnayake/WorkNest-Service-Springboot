package com.worknest.master.dto;

public record PlatformTenantAdminActionResponseDto(
        String tenantKey,
        String adminEmail,
        String action,
        boolean passwordChangeRequired) {
}
