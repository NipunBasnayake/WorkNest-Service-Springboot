package com.worknest.master.dto.subscription;

import java.util.List;

public record FeatureMatrixResponseDto(
        List<SubscriptionPlanResponseDto> plans,
        List<FeatureMatrixRowDto> features) {
}
