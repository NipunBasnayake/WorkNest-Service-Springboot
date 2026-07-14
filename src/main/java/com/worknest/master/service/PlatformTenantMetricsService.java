package com.worknest.master.service;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.datasource.TenantDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PlatformTenantMetricsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformTenantMetricsService.class);
    private static final List<PlatformRole> TENANT_ADMIN_ROLES = List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN);
    private static final String USAGE_SQL = """
            SELECT
              (SELECT COUNT(*) FROM employees) AS employee_count,
              (SELECT COUNT(*) FROM projects) AS project_count,
              (SELECT COUNT(*) FROM teams) AS team_count,
              (SELECT COUNT(*) FROM tasks) AS task_count,
              (SELECT MAX(created_at) FROM audit_logs) AS last_activity_at
            """;

    private final PlatformUserRepository platformUserRepository;
    private final TenantDataSourceService tenantDataSourceService;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public PlatformTenantMetricsService(
            PlatformUserRepository platformUserRepository,
            TenantDataSourceService tenantDataSourceService,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.platformUserRepository = platformUserRepository;
        this.tenantDataSourceService = tenantDataSourceService;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    public TenantMetrics collect(PlatformTenant tenant) {
        List<PlatformUser> users = masterTenantContextRunner.runInMasterContext(
                () -> platformUserRepository.findByTenantKeyIgnoreCase(tenant.getTenantKey()));
        PlatformUser admin = users.stream()
                .filter(user -> TENANT_ADMIN_ROLES.contains(user.getRole()))
                .min(Comparator.comparing(PlatformUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        LocalDateTime lastLoginAt = users.stream()
                .map(PlatformUser::getLastLoginAt)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        long employeeCount = users.size();
        long projectCount = 0;
        long teamCount = 0;
        long taskCount = 0;
        LocalDateTime lastActivityAt = lastLoginAt;
        boolean usageAvailable = false;

        if (tenant.getStatus() == TenantStatus.ACTIVE || tenant.getStatus() == TenantStatus.PROVISIONING) {
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(tenantDataSourceService.getDataSource(tenant.getTenantKey()));
                Map<String, Object> usage = jdbcTemplate.queryForMap(USAGE_SQL);
                employeeCount = numberValue(usage.get("employee_count"), employeeCount);
                projectCount = numberValue(usage.get("project_count"), 0);
                teamCount = numberValue(usage.get("team_count"), 0);
                taskCount = numberValue(usage.get("task_count"), 0);
                LocalDateTime tenantActivity = dateTimeValue(usage.get("last_activity_at"));
                if (tenantActivity != null && (lastActivityAt == null || tenantActivity.isAfter(lastActivityAt))) {
                    lastActivityAt = tenantActivity;
                }
                usageAvailable = true;
            } catch (RuntimeException ex) {
                log.warn("Platform usage metrics are unavailable for tenant {}: {}", tenant.getTenantKey(), ex.getMessage());
            }
        }

        return new TenantMetrics(
                admin == null ? null : admin.getFullName(),
                admin == null ? null : admin.getEmail(),
                employeeCount,
                projectCount,
                teamCount,
                taskCount,
                lastLoginAt,
                lastActivityAt,
                usageAvailable
        );
    }

    private long numberValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    public record TenantMetrics(
            String adminName,
            String adminEmail,
            long employeeCount,
            long projectCount,
            long teamCount,
            long taskCount,
            LocalDateTime lastLoginAt,
            LocalDateTime lastActivityAt,
            boolean usageAvailable) {
    }
}
