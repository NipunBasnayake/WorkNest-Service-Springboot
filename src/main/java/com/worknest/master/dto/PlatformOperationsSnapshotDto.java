package com.worknest.master.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlatformOperationsSnapshotDto(
        LocalDateTime generatedAt,
        TenantOverview tenants,
        UserOverview users,
        UsageOverview usage,
        List<MetricPoint> tenantStatusDistribution,
        List<TrendPoint> tenantGrowthTrend,
        List<TrendPoint> userGrowthTrend,
        List<TrendPoint> loginActivityTrend,
        List<MetricPoint> userRoleDistribution,
        List<PlatformTenantResponseDto> tenantHealth,
        List<PlatformAuditEventResponseDto> recentAuditEvents) {

    public record TenantOverview(
            long total,
            long active,
            long suspended,
            long inactive,
            long pending,
            long archived,
            long rejected,
            long newThisMonth,
            Double growthPercent) {
    }

    public record UserOverview(
            long total,
            long active,
            long newToday,
            long newThisMonth,
            long activeSessions,
            long loggedInLastSevenDays,
            long tenantAdminsInactiveThirtyDays) {
    }

    public record UsageOverview(
            double averageEmployeesPerTenant,
            double averageProjectsPerTenant,
            double averageTeamsPerTenant,
            double averageTasksPerTenant,
            long platformAuditEvents,
            long tenantsWithUsageAvailable) {
    }

    public record MetricPoint(String label, long value) {
    }

    public record TrendPoint(String label, long value, long cumulativeValue) {
    }
}
