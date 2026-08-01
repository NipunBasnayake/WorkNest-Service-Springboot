package com.worknest.master.dto.subscription;

import java.math.BigDecimal;

public record SubscriptionPlanResponseDto(
        Long id,
        String name,
        String code,
        String description,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String billingPeriod,
        String badge,
        boolean recommended,
        String color,
        String icon,
        boolean active,
        int displayOrder,
        long enabledFeatureCount,
        long totalFeatureCount,
        long tenantCount) {
}
