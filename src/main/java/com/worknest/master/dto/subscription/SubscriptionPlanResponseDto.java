package com.worknest.master.dto.subscription;

public record SubscriptionPlanResponseDto(
        Long id,
        String name,
        String code,
        String description,
        boolean active,
        int displayOrder,
        long enabledFeatureCount,
        long totalFeatureCount,
        long tenantCount) {
}
