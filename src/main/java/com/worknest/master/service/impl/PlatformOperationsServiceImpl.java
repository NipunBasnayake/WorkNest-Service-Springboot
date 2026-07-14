package com.worknest.master.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.dto.PlatformAuditEventResponseDto;
import com.worknest.master.dto.PlatformOperationsSnapshotDto;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.dto.PlatformUserResponseDto;
import com.worknest.master.entity.PlatformTenantStatusAudit;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import com.worknest.master.repository.PlatformTenantStatusAuditRepository;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.repository.RefreshTokenRepository;
import com.worknest.master.service.PlatformOperationsService;
import com.worknest.master.service.PlatformTenantService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "masterTransactionManager", readOnly = true)
public class PlatformOperationsServiceImpl implements PlatformOperationsService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final PlatformTenantService platformTenantService;
    private final PlatformUserRepository platformUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlatformTenantStatusAuditRepository auditRepository;
    private final PlatformTenantRepository tenantRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public PlatformOperationsServiceImpl(
            PlatformTenantService platformTenantService,
            PlatformUserRepository platformUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            PlatformTenantStatusAuditRepository auditRepository,
            PlatformTenantRepository tenantRepository,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.platformTenantService = platformTenantService;
        this.platformUserRepository = platformUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditRepository = auditRepository;
        this.tenantRepository = tenantRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Override
    @Cacheable(cacheNames = "platformOperations", key = "'snapshot'")
    public PlatformOperationsSnapshotDto getSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        List<PlatformTenantResponseDto> tenants = platformTenantService.getAllTenants();
        List<PlatformUser> users = loadUsers();
        List<RefreshToken> sessions = loadSessions();
        List<PlatformTenantStatusAudit> audits = loadAudits();

        Map<TenantStatus, Long> statusCounts = tenants.stream()
                .filter(tenant -> tenant.getStatus() != null)
                .collect(Collectors.groupingBy(
                        PlatformTenantResponseDto::getStatus,
                        () -> new EnumMap<>(TenantStatus.class),
                        Collectors.counting()));

        YearMonth currentMonth = YearMonth.from(now);
        long newThisMonth = tenants.stream().filter(tenant -> isInMonth(tenant.getCreatedAt(), currentMonth)).count();
        long newLastMonth = tenants.stream().filter(tenant -> isInMonth(tenant.getCreatedAt(), currentMonth.minusMonths(1))).count();
        Double growthPercent = percentageChange(newThisMonth, newLastMonth);

        long activeSessions = sessions.stream().filter(session -> isActive(session, now)).count();
        LocalDate today = now.toLocalDate();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        long activeUsers = users.stream().filter(user -> user.getStatus() == UserStatus.ACTIVE).count();
        long newUsersToday = users.stream().filter(user -> user.getCreatedAt() != null && user.getCreatedAt().toLocalDate().equals(today)).count();
        long newUsersThisMonth = users.stream().filter(user -> isInMonth(user.getCreatedAt(), currentMonth)).count();
        long loggedInLastSevenDays = users.stream().filter(user -> user.getLastLoginAt() != null && !user.getLastLoginAt().isBefore(sevenDaysAgo)).count();
        long inactiveTenantAdmins = users.stream()
                .filter(user -> user.getRole() != null && user.getRole().isTenantAdminEquivalent())
                .filter(user -> user.getLastLoginAt() == null || user.getLastLoginAt().isBefore(thirtyDaysAgo))
                .count();

        List<PlatformTenantResponseDto> usageTenants = tenants.stream()
                .filter(tenant -> Boolean.TRUE.equals(tenant.getUsageAvailable()))
                .toList();
        double usageDenominator = usageTenants.isEmpty() ? 1 : usageTenants.size();

        return new PlatformOperationsSnapshotDto(
                now,
                new PlatformOperationsSnapshotDto.TenantOverview(
                        tenants.size(),
                        count(statusCounts, TenantStatus.ACTIVE),
                        count(statusCounts, TenantStatus.SUSPENDED),
                        count(statusCounts, TenantStatus.INACTIVE),
                        count(statusCounts, TenantStatus.PROVISIONING),
                        count(statusCounts, TenantStatus.ARCHIVED),
                        count(statusCounts, TenantStatus.REJECTED),
                        newThisMonth,
                        growthPercent),
                new PlatformOperationsSnapshotDto.UserOverview(
                        users.size(), activeUsers, newUsersToday, newUsersThisMonth, activeSessions,
                        loggedInLastSevenDays, inactiveTenantAdmins),
                new PlatformOperationsSnapshotDto.UsageOverview(
                        average(usageTenants, PlatformTenantResponseDto::getEmployeeCount, usageDenominator),
                        average(usageTenants, PlatformTenantResponseDto::getProjectCount, usageDenominator),
                        average(usageTenants, PlatformTenantResponseDto::getTeamCount, usageDenominator),
                        average(usageTenants, PlatformTenantResponseDto::getTaskCount, usageDenominator),
                        countAudits(),
                        usageTenants.size()),
                tenantStatusDistribution(statusCounts),
                growthTrend(tenants.stream().map(PlatformTenantResponseDto::getCreatedAt).toList(), now),
                growthTrend(users.stream().map(PlatformUser::getCreatedAt).toList(), now),
                loginTrend(users, now),
                roleDistribution(users),
                tenants.stream()
                        .sorted(Comparator.comparing(
                                PlatformTenantResponseDto::getLastActivityAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList(),
                mapAuditEvents(audits, tenants.stream().collect(Collectors.toMap(
                        tenant -> normalize(tenant.getTenantKey()),
                        PlatformTenantResponseDto::getCompanyName,
                        (first, ignored) -> first)))
        );
    }

    @Override
    public List<PlatformUserResponseDto> getUsers() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> companies = loadCompanyNames();
        Map<Long, Long> activeSessionCounts = loadSessions().stream()
                .filter(session -> isActive(session, now))
                .filter(session -> session.getPlatformUser() != null && session.getPlatformUser().getId() != null)
                .collect(Collectors.groupingBy(session -> session.getPlatformUser().getId(), Collectors.counting()));

        return loadUsers().stream()
                .sorted(Comparator.comparing(PlatformUser::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(user -> new PlatformUserResponseDto(
                        user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getStatus(),
                        user.getTenantKey(), companies.get(normalize(user.getTenantKey())),
                        user.getLastLoginAt(), user.getCreatedAt(), activeSessionCounts.getOrDefault(user.getId(), 0L)))
                .toList();
    }

    @Override
    public List<PlatformAuditEventResponseDto> getAuditEvents() {
        return mapAuditEvents(loadAudits(), loadCompanyNames());
    }

    private List<PlatformUser> loadUsers() {
        return masterTenantContextRunner.runInMasterContext(() -> platformUserRepository.findAll());
    }

    private List<RefreshToken> loadSessions() {
        return masterTenantContextRunner.runInMasterContext(() -> refreshTokenRepository.findAll());
    }

    private List<PlatformTenantStatusAudit> loadAudits() {
        return masterTenantContextRunner.runInMasterContext(auditRepository::findTop100ByOrderByChangedAtDesc);
    }

    private long countAudits() {
        return masterTenantContextRunner.runInMasterContext(() -> auditRepository.count());
    }

    private List<PlatformAuditEventResponseDto> mapAuditEvents(
            List<PlatformTenantStatusAudit> audits,
            Map<String, String> companies) {
        return audits.stream().map(audit -> new PlatformAuditEventResponseDto(
                audit.getId(), audit.getTenantKey(), companies.get(normalize(audit.getTenantKey())),
                audit.getActorEmail(), "TENANT_STATUS_CHANGED", audit.getPreviousStatus(),
                audit.getNewStatus(), audit.getChangedAt())).toList();
    }

    private Map<String, String> loadCompanyNames() {
        return masterTenantContextRunner.runInMasterContext(() -> tenantRepository.findAll().stream()
                .collect(Collectors.toMap(
                        tenant -> normalize(tenant.getTenantKey()),
                        tenant -> tenant.getCompanyName(),
                        (first, ignored) -> first)));
    }

    private List<PlatformOperationsSnapshotDto.MetricPoint> tenantStatusDistribution(Map<TenantStatus, Long> counts) {
        List<TenantStatus> order = List.of(
                TenantStatus.ACTIVE, TenantStatus.PROVISIONING, TenantStatus.SUSPENDED,
                TenantStatus.INACTIVE, TenantStatus.ARCHIVED, TenantStatus.REJECTED);
        return order.stream()
                .map(status -> new PlatformOperationsSnapshotDto.MetricPoint(label(status.name()), count(counts, status)))
                .toList();
    }

    private List<PlatformOperationsSnapshotDto.MetricPoint> roleDistribution(List<PlatformUser> users) {
        Map<String, Long> counts = users.stream()
                .filter(user -> user.getRole() != null)
                .collect(Collectors.groupingBy(user -> label(user.getRole().name()), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new PlatformOperationsSnapshotDto.MetricPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<PlatformOperationsSnapshotDto.TrendPoint> growthTrend(
            List<LocalDateTime> createdDates,
            LocalDateTime now) {
        YearMonth firstMonth = YearMonth.from(now).minusMonths(11);
        Map<YearMonth, Long> counts = createdDates.stream()
                .filter(value -> value != null)
                .collect(Collectors.groupingBy(YearMonth::from, Collectors.counting()));
        long cumulativeBeforeWindow = createdDates.stream()
                .filter(value -> value != null && YearMonth.from(value).isBefore(firstMonth))
                .count();
        List<PlatformOperationsSnapshotDto.TrendPoint> points = new ArrayList<>();
        long cumulative = cumulativeBeforeWindow;
        for (int index = 0; index < 12; index++) {
            YearMonth month = firstMonth.plusMonths(index);
            long value = counts.getOrDefault(month, 0L);
            cumulative += value;
            points.add(new PlatformOperationsSnapshotDto.TrendPoint(month.format(MONTH_LABEL), value, cumulative));
        }
        return points;
    }

    private List<PlatformOperationsSnapshotDto.TrendPoint> loginTrend(List<PlatformUser> users, LocalDateTime now) {
        LocalDate firstDay = now.toLocalDate().minusDays(13);
        Map<LocalDate, Long> counts = users.stream()
                .map(PlatformUser::getLastLoginAt)
                .filter(value -> value != null && !value.toLocalDate().isBefore(firstDay))
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
        List<PlatformOperationsSnapshotDto.TrendPoint> points = new ArrayList<>();
        long cumulative = 0;
        for (int index = 0; index < 14; index++) {
            LocalDate day = firstDay.plusDays(index);
            long value = counts.getOrDefault(day, 0L);
            cumulative += value;
            points.add(new PlatformOperationsSnapshotDto.TrendPoint(day.format(DAY_LABEL), value, cumulative));
        }
        return points;
    }

    private double average(
            List<PlatformTenantResponseDto> tenants,
            Function<PlatformTenantResponseDto, Long> getter,
            double denominator) {
        if (tenants.isEmpty()) return 0;
        long sum = tenants.stream().map(getter).filter(value -> value != null).mapToLong(Long::longValue).sum();
        return Math.round((sum / denominator) * 10.0) / 10.0;
    }

    private boolean isActive(RefreshToken token, LocalDateTime now) {
        return token != null && !token.isRevoked() && token.getExpiresAt() != null && token.getExpiresAt().isAfter(now);
    }

    private boolean isInMonth(LocalDateTime date, YearMonth month) {
        return date != null && YearMonth.from(date).equals(month);
    }

    private Double percentageChange(long current, long previous) {
        if (previous == 0) return current == 0 ? 0.0 : null;
        return Math.round((((double) current - previous) / previous) * 1000.0) / 10.0;
    }

    private long count(Map<TenantStatus, Long> counts, TenantStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String label(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        for (int index = 0; index < words.length; index++) {
            words[index] = Character.toUpperCase(words[index].charAt(0)) + words[index].substring(1);
        }
        return String.join(" ", words);
    }
}
