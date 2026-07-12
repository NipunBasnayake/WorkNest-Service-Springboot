package com.worknest.tenant.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Chart-ready tenant BI read model; clients never calculate from operational rows. */
public record BusinessIntelligenceReportDto(
        Instant generatedAt,
        DateRangeDto range,
        List<MetricDto> kpis,
        Map<String, List<ChartPointDto>> charts,
        List<InsightDto> insights,
        List<RiskDto> risks,
        List<ActivityDto> recentActivities,
        FilterOptionsDto filterOptions
) {
    public record DateRangeDto(String fromDate, String toDate) {}
    public record MetricDto(String key, String label, double value, String unit, Double changePercent, String tone, String context) {}
    public record ChartPointDto(String label, double value, Double secondaryValue, Double tertiaryValue, String id) {}
    public record InsightDto(String id, String severity, String title, String description, String route) {}
    public record RiskDto(String id, String severity, String title, String description, long count, String route) {}
    public record ActivityDto(String id, String actor, String action, String entityType, String occurredAt) {}
    public record OptionDto(String value, String label) {}
    public record FilterOptionsDto(
            List<OptionDto> departments,
            List<OptionDto> projects,
            List<OptionDto> teams,
            List<OptionDto> employees,
            List<OptionDto> taskStatuses,
            List<OptionDto> recruitmentStatuses,
            List<OptionDto> leaveTypes
    ) {}
}
