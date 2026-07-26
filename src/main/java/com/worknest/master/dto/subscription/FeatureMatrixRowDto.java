package com.worknest.master.dto.subscription;

import com.worknest.master.enums.FeatureKey;

import java.util.Map;

public record FeatureMatrixRowDto(
        Long featureId,
        FeatureKey featureKey,
        String displayName,
        Map<String, Boolean> plans) {
}
