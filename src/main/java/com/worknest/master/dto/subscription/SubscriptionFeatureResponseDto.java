package com.worknest.master.dto.subscription;

import com.worknest.master.enums.FeatureKey;

public record SubscriptionFeatureResponseDto(
        Long id,
        FeatureKey featureKey,
        String displayName) {
}
