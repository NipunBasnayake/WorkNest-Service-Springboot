package com.worknest.master.dto.subscription;

import java.util.List;

public record TenantPackageCatalogDto(
        CurrentSubscriptionAccessDto currentSubscription,
        List<SubscriptionPlanResponseDto> plans,
        FeatureMatrixResponseDto featureMatrix) {
}
