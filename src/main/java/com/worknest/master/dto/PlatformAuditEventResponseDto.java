package com.worknest.master.dto;

import com.worknest.common.enums.TenantStatus;

import java.time.LocalDateTime;

public record PlatformAuditEventResponseDto(
        Long id,
        String tenantKey,
        String companyName,
        String actorEmail,
        String action,
        TenantStatus previousStatus,
        TenantStatus newStatus,
        LocalDateTime occurredAt) {
}
