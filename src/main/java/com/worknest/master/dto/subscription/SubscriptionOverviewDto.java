package com.worknest.master.dto.subscription;

import java.util.List;

public record SubscriptionOverviewDto(
        SubscriptionStatisticsDto statistics,
        List<SubscriptionPlanResponseDto> plans,
        List<TenantSubscriptionResponseDto> tenantSubscriptions,
        FeatureMatrixResponseDto featureMatrix) {
}
