package com.worknest.master.dto;

import com.worknest.common.enums.TenantStatus;

import java.time.LocalDateTime;

public record TenantBrandingViewDto(
        Long tenantId,
        String tenantKey,
        String tenantSlug,
        String companyName,
        String primaryColor,
        Long brandingVersion,
        Integer tokenAlgorithmVersion,
        BrandingLogoDto logo,
        TenantStatus status,
        LocalDateTime updatedAt) {
}
