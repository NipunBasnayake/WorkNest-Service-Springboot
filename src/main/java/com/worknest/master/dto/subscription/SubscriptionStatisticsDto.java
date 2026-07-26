package com.worknest.master.dto.subscription;

import java.util.Map;

public record SubscriptionStatisticsDto(
        long totalTenants,
        Map<String, Long> planDistribution,
        long recentlyUpgraded,
        long recentlyExpired) {
}
