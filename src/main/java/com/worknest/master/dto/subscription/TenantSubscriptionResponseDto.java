package com.worknest.master.dto.subscription;

import java.time.LocalDateTime;

public record TenantSubscriptionResponseDto(
        Long id,
        Long tenantId,
        String tenantKey,
        String tenantName,
        Long planId,
        String planName,
        String planCode,
        LocalDateTime assignedDate,
        LocalDateTime expiresAt,
        boolean active,
        String status) {
}
