package com.worknest.master.dto.subscription;

import java.util.Map;

public record SubscriptionStatisticsDto(
        long totalPackages,
        long activePackages,
        long totalTenants,
        long subscribedTenants,
        Map<String, Long> planDistribution,
        String mostPopularPackage,
        long recentlyUpgraded,
        long recentlyExpired) {
}
