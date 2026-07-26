package com.worknest.master.dto.subscription;

import com.worknest.master.enums.FeatureKey;

import java.time.LocalDateTime;
import java.util.Set;

public record CurrentSubscriptionAccessDto(
        String planName,
        String planCode,
        LocalDateTime assignedDate,
        LocalDateTime expiresAt,
        boolean active,
        String status,
        Set<FeatureKey> features) {
}
