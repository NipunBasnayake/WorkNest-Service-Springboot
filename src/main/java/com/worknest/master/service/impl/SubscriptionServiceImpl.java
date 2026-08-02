package com.worknest.master.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.subscription.CurrentSubscriptionAccessDto;
import com.worknest.master.dto.subscription.FeatureMatrixResponseDto;
import com.worknest.master.dto.subscription.FeatureMatrixRowDto;
import com.worknest.master.dto.subscription.SubscriptionFeatureResponseDto;
import com.worknest.master.dto.subscription.SubscriptionOverviewDto;
import com.worknest.master.dto.subscription.SubscriptionPlanRequestDto;
import com.worknest.master.dto.subscription.SubscriptionPlanResponseDto;
import com.worknest.master.dto.subscription.SubscriptionStatisticsDto;
import com.worknest.master.dto.subscription.TenantPackageCatalogDto;
import com.worknest.master.dto.subscription.TenantPlanAssignmentRequestDto;
import com.worknest.master.dto.subscription.TenantSubscriptionResponseDto;
import com.worknest.master.entity.PlanFeature;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.SubscriptionAuditLog;
import com.worknest.master.entity.SubscriptionFeature;
import com.worknest.master.entity.SubscriptionPlan;
import com.worknest.master.entity.TenantSubscription;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.enums.SubscriptionAuditAction;
import com.worknest.master.enums.SubscriptionChangeSource;
import com.worknest.master.repository.PlanFeatureRepository;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.SubscriptionAuditLogRepository;
import com.worknest.master.repository.SubscriptionFeatureRepository;
import com.worknest.master.repository.SubscriptionRepository;
import com.worknest.master.repository.TenantSubscriptionRepository;
import com.worknest.master.service.SubscriptionService;
import com.worknest.security.util.SecurityUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "masterTransactionManager", readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    public static final String FREE_PLAN_CODE = "FREE";
    private static final String SYSTEM_ACTOR = "system@worknest.local";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionFeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionAuditLogRepository auditRepository;
    private final PlatformTenantRepository tenantRepository;
    private final SecurityUtils securityUtils;
    private final Clock clock;

    public SubscriptionServiceImpl(
            SubscriptionRepository subscriptionRepository,
            SubscriptionFeatureRepository featureRepository,
            PlanFeatureRepository planFeatureRepository,
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionAuditLogRepository auditRepository,
            PlatformTenantRepository tenantRepository,
            SecurityUtils securityUtils,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.auditRepository = auditRepository;
        this.tenantRepository = tenantRepository;
        this.securityUtils = securityUtils;
        this.clock = clock;
    }

    @Override
    public SubscriptionOverviewDto getOverview() {
        List<SubscriptionPlanResponseDto> plans = getPlans();
        List<TenantSubscriptionResponseDto> subscriptions = getTenantSubscriptions();
        return new SubscriptionOverviewDto(
                getStatistics(),
                plans,
                subscriptions,
                buildFeatureMatrix(plans));
    }

    @Override
    public List<SubscriptionPlanResponseDto> getPlans() {
        List<SubscriptionPlan> plans = subscriptionRepository.findAllByOrderByDisplayOrderAscNameAsc();
        List<PlanFeature> planFeatures = planFeatureRepository.findAllByOrderByFeatureFeatureKeyAscPlanDisplayOrderAsc();
        List<TenantSubscription> subscriptions = tenantSubscriptionRepository.findAllByOrderByTenantCompanyNameAsc();
        long totalFeatureCount = featureRepository.count();

        Map<Long, Long> enabledCounts = planFeatures.stream()
                .filter(PlanFeature::isEnabled)
                .collect(Collectors.groupingBy(
                        item -> item.getPlan().getId(),
                        Collectors.counting()));
        Map<Long, Long> tenantCounts = subscriptions.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getSubscriptionPlan().getId(),
                        Collectors.counting()));

        return plans.stream()
                .map(plan -> toPlanResponse(
                        plan,
                        enabledCounts.getOrDefault(plan.getId(), 0L),
                        totalFeatureCount,
                        tenantCounts.getOrDefault(plan.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public SubscriptionPlanResponseDto createPlan(SubscriptionPlanRequestDto request) {
        String code = normalizeCode(request.code());
        if (subscriptionRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("A subscription plan with this code already exists");
        }

        SubscriptionPlan plan = applyPlanRequest(new SubscriptionPlan(), request);
        SubscriptionPlan saved = subscriptionRepository.saveAndFlush(plan);
        for (SubscriptionFeature feature : featureRepository.findAllByOrderByFeatureKeyAsc()) {
            PlanFeature planFeature = new PlanFeature();
            planFeature.setPlan(saved);
            planFeature.setFeature(feature);
            planFeature.setEnabled(true);
            planFeatureRepository.save(planFeature);
        }
        audit(null, saved, SubscriptionAuditAction.PLAN_CREATED, null, saved.getCode(),
                SubscriptionChangeSource.MANUAL, currentActor(), "Plan created");
        return findPlanResponse(saved.getId());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public SubscriptionPlanResponseDto updatePlan(Long planId, SubscriptionPlanRequestDto request) {
        SubscriptionPlan plan = findPlan(planId);
        String code = normalizeCode(request.code());
        if (subscriptionRepository.existsByCodeIgnoreCaseAndIdNot(code, planId)) {
            throw new BadRequestException("A subscription plan with this code already exists");
        }
        if (FREE_PLAN_CODE.equals(plan.getCode()) && !FREE_PLAN_CODE.equals(code)) {
            throw new BadRequestException("The FREE plan code is reserved for automatic tenant assignment");
        }
        if (FREE_PLAN_CODE.equals(plan.getCode()) && !Boolean.TRUE.equals(request.active())) {
            throw new BadRequestException("The FREE plan must remain active for automatic tenant assignment");
        }

        String previousCode = plan.getCode();
        applyPlanRequest(plan, request);
        SubscriptionPlan saved = subscriptionRepository.saveAndFlush(plan);
        audit(null, saved, SubscriptionAuditAction.PLAN_UPDATED, previousCode, saved.getCode(),
                SubscriptionChangeSource.MANUAL, currentActor(), "Plan details updated");
        return findPlanResponse(saved.getId());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public SubscriptionPlanResponseDto setPlanActive(Long planId, boolean active) {
        SubscriptionPlan plan = findPlan(planId);
        if (FREE_PLAN_CODE.equals(plan.getCode()) && !active) {
            throw new BadRequestException("The FREE plan must remain active for automatic tenant assignment");
        }
        if (plan.isActive() == active) {
            return findPlanResponse(planId);
        }
        plan.setActive(active);
        subscriptionRepository.saveAndFlush(plan);
        audit(null, plan, SubscriptionAuditAction.PLAN_STATUS_CHANGED, plan.getCode(), plan.getCode(),
                SubscriptionChangeSource.MANUAL, currentActor(),
                active ? "Plan enabled" : "Plan disabled");
        return findPlanResponse(planId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public void deletePlan(Long planId) {
        SubscriptionPlan plan = findPlan(planId);
        if (FREE_PLAN_CODE.equals(plan.getCode())) {
            throw new BadRequestException("The FREE plan cannot be deleted because it is the registration default");
        }
        if (tenantSubscriptionRepository.existsBySubscriptionPlanId(planId)) {
            throw new BadRequestException("Packages assigned to tenants cannot be deleted. Deactivate the package instead.");
        }
        planFeatureRepository.deleteByPlanId(planId);
        subscriptionRepository.delete(plan);
        audit(null, plan, SubscriptionAuditAction.PLAN_UPDATED, plan.getCode(), null,
                SubscriptionChangeSource.MANUAL, currentActor(), "Plan deleted");
    }

    @Override
    public List<SubscriptionFeatureResponseDto> getFeatures() {
        return featureRepository.findAllByOrderByFeatureKeyAsc().stream()
                .map(feature -> new SubscriptionFeatureResponseDto(
                        feature.getId(),
                        feature.getFeatureKey(),
                        displayName(feature.getFeatureKey())))
                .toList();
    }

    @Override
    public FeatureMatrixResponseDto getFeatureMatrix() {
        List<SubscriptionPlanResponseDto> plans = getPlans();
        return buildFeatureMatrix(plans);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public FeatureMatrixResponseDto setPlanFeature(Long planId, FeatureKey featureKey, boolean enabled) {
        SubscriptionPlan plan = findPlan(planId);
        PlanFeature planFeature = planFeatureRepository
                .findByPlanIdAndFeatureFeatureKey(planId, featureKey)
                .orElseGet(() -> {
                    SubscriptionFeature feature = featureRepository.findByFeatureKey(featureKey)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Subscription feature not found: " + featureKey));
                    PlanFeature created = new PlanFeature();
                    created.setPlan(plan);
                    created.setFeature(feature);
                    return created;
                });
        planFeature.setEnabled(enabled);
        planFeatureRepository.saveAndFlush(planFeature);
        audit(null, plan, SubscriptionAuditAction.FEATURE_UPDATED, plan.getCode(), plan.getCode(),
                SubscriptionChangeSource.MANUAL, currentActor(),
                featureKey.name() + " " + (enabled ? "enabled" : "disabled"));
        return getFeatureMatrix();
    }

    @Override
    public List<TenantSubscriptionResponseDto> getTenantSubscriptions() {
        LocalDateTime now = LocalDateTime.now(clock);
        return tenantSubscriptionRepository.findAllByOrderByTenantCompanyNameAsc().stream()
                .map(subscription -> toTenantSubscriptionResponse(subscription, now))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public TenantSubscriptionResponseDto assignTenantPlan(
            String tenantKey,
            TenantPlanAssignmentRequestDto request) {
        return changeTenantPlan(
                tenantKey,
                request.planCode(),
                request.expiresAt(),
                SubscriptionChangeSource.MANUAL,
                currentActor());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public TenantSubscriptionResponseDto selectTenantPackage(String tenantKey, String planCode) {
        return changeTenantPlan(
                tenantKey,
                planCode,
                null,
                SubscriptionChangeSource.MANUAL,
                currentActor());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public TenantSubscriptionResponseDto changeTenantPlan(
            String tenantKey,
            String planCode,
            LocalDateTime expiresAt,
            SubscriptionChangeSource source,
            String actor) {
        PlatformTenant tenant = findTenant(tenantKey);
        SubscriptionPlan newPlan = findActivePlan(planCode);
        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenant.getId())
                .orElseGet(() -> {
                    TenantSubscription created = new TenantSubscription();
                    created.setTenant(tenant);
                    return created;
                });

        SubscriptionPlan previousPlan = subscription.getSubscriptionPlan();
        SubscriptionAuditAction action = resolveAssignmentAction(previousPlan, newPlan);
        subscription.setSubscriptionPlan(newPlan);
        subscription.setAssignedDate(LocalDateTime.now(clock));
        subscription.setExpiresAt(expiresAt);
        subscription.setActive(true);
        TenantSubscription saved = tenantSubscriptionRepository.saveAndFlush(subscription);

        audit(tenant, newPlan, action,
                previousPlan == null ? null : previousPlan.getCode(),
                newPlan.getCode(),
                source == null ? SubscriptionChangeSource.SYSTEM : source,
                normalizeActor(actor, source),
                expiresAt == null ? "Unlimited duration" : "Expires at " + expiresAt);
        return toTenantSubscriptionResponse(saved, LocalDateTime.now(clock));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public TenantSubscriptionResponseDto deactivateTenantSubscription(String tenantKey) {
        PlatformTenant tenant = findTenant(tenantKey);
        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant subscription not found: " + tenant.getTenantKey()));
        if (!subscription.isActive()) {
            return toTenantSubscriptionResponse(subscription, LocalDateTime.now(clock));
        }
        subscription.setActive(false);
        TenantSubscription saved = tenantSubscriptionRepository.saveAndFlush(subscription);
        audit(tenant, subscription.getSubscriptionPlan(), SubscriptionAuditAction.DEACTIVATED,
                subscription.getSubscriptionPlan().getCode(), subscription.getSubscriptionPlan().getCode(),
                SubscriptionChangeSource.MANUAL, currentActor(), "Subscription deactivated");
        return toTenantSubscriptionResponse(saved, LocalDateTime.now(clock));
    }

    @Override
    public CurrentSubscriptionAccessDto getCurrentAccess(String tenantKey) {
        PlatformTenant tenant = findTenantByKeyOrSlug(tenantKey);
        TenantSubscription subscription = tenantSubscriptionRepository
                .findByTenantId(tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant subscription not found: " + tenant.getTenantKey()));
        LocalDateTime now = LocalDateTime.now(clock);
        Set<FeatureKey> features = subscription.isEffectiveAt(now)
                ? planFeatureRepository.findByPlanId(subscription.getSubscriptionPlan().getId()).stream()
                    .filter(PlanFeature::isEnabled)
                    .map(item -> item.getFeature().getFeatureKey())
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        return new CurrentSubscriptionAccessDto(
                subscription.getSubscriptionPlan().getName(),
                subscription.getSubscriptionPlan().getCode(),
                subscription.getAssignedDate(),
                subscription.getExpiresAt(),
                subscription.isActive(),
                status(subscription, now),
                features);
    }

    @Override
    public TenantPackageCatalogDto getTenantPackageCatalog(String tenantKey) {
        CurrentSubscriptionAccessDto currentAccess = getCurrentAccess(tenantKey);
        List<SubscriptionPlanResponseDto> activePlans = getPlans().stream()
                .filter(SubscriptionPlanResponseDto::active)
                .toList();
        return new TenantPackageCatalogDto(
                currentAccess,
                activePlans,
                buildFeatureMatrix(activePlans));
    }

    @Override
    public SubscriptionStatisticsDto getStatistics() {
        List<TenantSubscription> subscriptions =
                tenantSubscriptionRepository.findAllByOrderByTenantCompanyNameAsc();
        Map<String, Long> distribution = new LinkedHashMap<>();
        subscriptionRepository.findAllByOrderByDisplayOrderAscNameAsc()
                .forEach(plan -> distribution.put(plan.getCode(), 0L));
        subscriptions.forEach(subscription -> distribution.compute(
                subscription.getSubscriptionPlan().getCode(),
                (ignored, count) -> count == null ? 1L : count + 1));
        String mostPopularPackage = distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        return new SubscriptionStatisticsDto(
                subscriptionRepository.count(),
                subscriptionRepository.countByActiveTrue(),
                tenantRepository.count(),
                tenantSubscriptionRepository.countByActiveTrue(),
                distribution,
                mostPopularPackage,
                auditRepository.countByActionAndOccurredAtAfter(
                        SubscriptionAuditAction.UPGRADED,
                        thirtyDaysAgo),
                tenantSubscriptionRepository.countByExpiresAtBetween(thirtyDaysAgo, now));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void assignDefaultPlan(PlatformTenant tenant) {
        if (tenant == null || tenant.getId() == null
                || tenantSubscriptionRepository.existsByTenantId(tenant.getId())) {
            return;
        }
        SubscriptionPlan free = subscriptionRepository.findByCodeIgnoreCase(FREE_PLAN_CODE)
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "The active FREE subscription plan has not been initialized"));
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(free);
        subscription.setAssignedDate(LocalDateTime.now(clock));
        subscription.setExpiresAt(null);
        subscription.setActive(true);
        tenantSubscriptionRepository.save(subscription);
        audit(tenant, free, SubscriptionAuditAction.ASSIGNED, null, free.getCode(),
                SubscriptionChangeSource.SYSTEM, SYSTEM_ACTOR, "Automatically assigned during tenant registration");
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void bootstrapDefaults() {
        List<SubscriptionPlanSeed> seeds = List.of(
                new SubscriptionPlanSeed("Free", FREE_PLAN_CODE,
                        "Core WorkNest capabilities with unlimited duration.",
                        BigDecimal.ZERO, BigDecimal.ZERO, "MONTHLY", "Free forever",
                        false, "#64748b", "sparkles", 10,
                        Set.of(FeatureKey.DASHBOARD, FeatureKey.EMPLOYEE, FeatureKey.PROJECTS,
                                FeatureKey.TASKS, FeatureKey.NOTIFICATIONS, FeatureKey.SETTINGS)),
                new SubscriptionPlanSeed("Starter", "STARTER",
                        "A foundation for growing teams.",
                        BigDecimal.ZERO, BigDecimal.ZERO, "MONTHLY", "Starter",
                        false, "#2563eb", "rocket", 20,
                        Set.of(FeatureKey.DASHBOARD, FeatureKey.EMPLOYEE, FeatureKey.TEAMS,
                                FeatureKey.PROJECTS, FeatureKey.TASKS, FeatureKey.ATTENDANCE,
                                FeatureKey.LEAVE, FeatureKey.NOTIFICATIONS, FeatureKey.SETTINGS)),
                new SubscriptionPlanSeed("Professional", "PROFESSIONAL",
                        "Advanced capabilities for established organizations.",
                        BigDecimal.ZERO, BigDecimal.ZERO, "MONTHLY", "Recommended",
                        true, "#7c3aed", "briefcase", 30,
                        Set.of(FeatureKey.DASHBOARD, FeatureKey.EMPLOYEE, FeatureKey.TEAMS,
                                FeatureKey.PROJECTS, FeatureKey.TASKS, FeatureKey.ATTENDANCE,
                                FeatureKey.LEAVE, FeatureKey.RECRUITMENT, FeatureKey.REPORTS,
                                FeatureKey.NOTIFICATIONS, FeatureKey.CHAT, FeatureKey.ANALYTICS,
                                FeatureKey.ANNOUNCEMENTS, FeatureKey.SETTINGS)),
                new SubscriptionPlanSeed("Enterprise", "ENTERPRISE",
                        "Flexible enterprise-grade workspace capabilities.",
                        BigDecimal.ZERO, BigDecimal.ZERO, "YEARLY", "Enterprise",
                        false, "#059669", "shield", 40,
                        Set.of(FeatureKey.values())));

        for (SubscriptionPlanSeed seed : seeds) {
            subscriptionRepository.findByCodeIgnoreCase(seed.code())
                    .map(plan -> {
                        applySeedMetadata(plan, seed);
                        return subscriptionRepository.save(plan);
                    })
                    .orElseGet(() -> {
                        SubscriptionPlan plan = new SubscriptionPlan();
                        plan.setName(seed.name());
                        plan.setCode(seed.code());
                        plan.setActive(true);
                        applySeedMetadata(plan, seed);
                        return subscriptionRepository.save(plan);
                    });
        }

        Map<FeatureKey, SubscriptionFeature> features = Arrays.stream(FeatureKey.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        featureKey -> featureRepository.findByFeatureKey(featureKey)
                                .orElseGet(() -> {
                                    SubscriptionFeature feature = new SubscriptionFeature();
                                    feature.setFeatureKey(featureKey);
                                    return featureRepository.save(feature);
                                }),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        for (SubscriptionPlan plan : subscriptionRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            for (SubscriptionFeature feature : features.values()) {
                if (!planFeatureRepository.existsByPlanIdAndFeatureId(plan.getId(), feature.getId())) {
                    PlanFeature planFeature = new PlanFeature();
                    planFeature.setPlan(plan);
                    planFeature.setFeature(feature);
                    planFeature.setEnabled(seedFeatures(plan.getCode()).contains(feature.getFeatureKey()));
                    planFeatureRepository.save(planFeature);
                }
            }
        }

        for (PlatformTenant tenant : tenantRepository.findAll()) {
            assignDefaultPlan(tenant);
        }
    }

    private FeatureMatrixResponseDto buildFeatureMatrix(List<SubscriptionPlanResponseDto> plans) {
        List<SubscriptionFeature> features = featureRepository.findAllByOrderByFeatureKeyAsc();
        Map<Long, Map<FeatureKey, Boolean>> settings = planFeatureRepository
                .findAllByOrderByFeatureFeatureKeyAscPlanDisplayOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getPlan().getId(),
                        Collectors.toMap(
                                item -> item.getFeature().getFeatureKey(),
                                PlanFeature::isEnabled)));

        List<FeatureMatrixRowDto> rows = features.stream().map(feature -> {
            Map<String, Boolean> values = new LinkedHashMap<>();
            plans.forEach(plan -> values.put(
                    plan.code(),
                    settings.getOrDefault(plan.id(), Map.of())
                            .getOrDefault(feature.getFeatureKey(), false)));
            return new FeatureMatrixRowDto(
                    feature.getId(),
                    feature.getFeatureKey(),
                    displayName(feature.getFeatureKey()),
                    values);
        }).toList();
        return new FeatureMatrixResponseDto(plans, rows);
    }

    private SubscriptionPlan applyPlanRequest(
            SubscriptionPlan plan,
            SubscriptionPlanRequestDto request) {
        plan.setName(request.name());
        plan.setCode(normalizeCode(request.code()));
        plan.setDescription(request.description());
        plan.setMonthlyPrice(request.monthlyPrice() == null ? BigDecimal.ZERO : request.monthlyPrice());
        plan.setYearlyPrice(request.yearlyPrice() == null ? BigDecimal.ZERO : request.yearlyPrice());
        plan.setBillingPeriod(defaultString(request.billingPeriod(), "MONTHLY"));
        plan.setBadge(blankToNull(request.badge()));
        plan.setRecommended(Boolean.TRUE.equals(request.recommended()));
        plan.setColor(defaultString(request.color(), "#2563eb"));
        plan.setIcon(defaultString(request.icon(), "package"));
        plan.setActive(Boolean.TRUE.equals(request.active()));
        plan.setDisplayOrder(request.displayOrder());
        return plan;
    }

    private SubscriptionPlanResponseDto findPlanResponse(Long planId) {
        return getPlans().stream()
                .filter(plan -> plan.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found with id: " + planId));
    }

    private SubscriptionPlanResponseDto toPlanResponse(
            SubscriptionPlan plan,
            long enabledFeatureCount,
            long totalFeatureCount,
            long tenantCount) {
        return new SubscriptionPlanResponseDto(
                plan.getId(),
                plan.getName(),
                plan.getCode(),
                plan.getDescription(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getBillingPeriod(),
                plan.getBadge(),
                plan.isRecommended(),
                plan.getColor(),
                plan.getIcon(),
                plan.isActive(),
                plan.getDisplayOrder(),
                enabledFeatureCount,
                totalFeatureCount,
                tenantCount);
    }

    private TenantSubscriptionResponseDto toTenantSubscriptionResponse(
            TenantSubscription subscription,
            LocalDateTime now) {
        return new TenantSubscriptionResponseDto(
                subscription.getId(),
                subscription.getTenant().getId(),
                subscription.getTenant().getTenantKey(),
                subscription.getTenant().getCompanyName(),
                subscription.getSubscriptionPlan().getId(),
                subscription.getSubscriptionPlan().getName(),
                subscription.getSubscriptionPlan().getCode(),
                subscription.getAssignedDate(),
                subscription.getExpiresAt(),
                subscription.isActive(),
                status(subscription, now));
    }

    private String status(TenantSubscription subscription, LocalDateTime now) {
        if (!subscription.isActive()) {
            return "INACTIVE";
        }
        if (subscription.getExpiresAt() != null && !subscription.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        if (!subscription.getSubscriptionPlan().isActive()) {
            return "PLAN_DISABLED";
        }
        return "ACTIVE";
    }

    private SubscriptionAuditAction resolveAssignmentAction(
            SubscriptionPlan previousPlan,
            SubscriptionPlan newPlan) {
        if (previousPlan == null) {
            return SubscriptionAuditAction.ASSIGNED;
        }
        if (previousPlan.getId().equals(newPlan.getId())) {
            return SubscriptionAuditAction.REASSIGNED;
        }
        return newPlan.getDisplayOrder() > previousPlan.getDisplayOrder()
                ? SubscriptionAuditAction.UPGRADED
                : SubscriptionAuditAction.DOWNGRADED;
    }

    private void audit(
            PlatformTenant tenant,
            SubscriptionPlan plan,
            SubscriptionAuditAction action,
            String previousPlanCode,
            String newPlanCode,
            SubscriptionChangeSource source,
            String actor,
            String details) {
        SubscriptionAuditLog audit = new SubscriptionAuditLog();
        audit.setTenantId(tenant == null ? null : tenant.getId());
        audit.setTenantKey(tenant == null ? null : tenant.getTenantKey());
        audit.setPlanId(plan == null ? null : plan.getId());
        audit.setAction(action);
        audit.setPreviousPlanCode(previousPlanCode);
        audit.setNewPlanCode(newPlanCode);
        audit.setSource(source == null ? SubscriptionChangeSource.SYSTEM : source);
        audit.setActorEmail(normalizeActor(actor, source));
        audit.setDetails(details);
        auditRepository.save(audit);
    }

    private SubscriptionPlan findPlan(Long planId) {
        if (planId == null) {
            throw new BadRequestException("Subscription plan id is required");
        }
        return subscriptionRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found with id: " + planId));
    }

    private SubscriptionPlan findActivePlan(String planCode) {
        SubscriptionPlan plan = subscriptionRepository.findByCodeIgnoreCase(normalizeCode(planCode))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found: " + planCode));
        if (!plan.isActive()) {
            throw new BadRequestException("Inactive subscription plans cannot be assigned");
        }
        return plan;
    }

    private PlatformTenant findTenant(String tenantKey) {
        String normalized = normalizeTenantKey(tenantKey);
        return tenantRepository.findByTenantKey(normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with key: " + normalized));
    }

    private PlatformTenant findTenantByKeyOrSlug(String tenantIdentifier) {
        String normalized = normalizeTenantKey(tenantIdentifier);
        return tenantRepository.findByTenantKey(normalized)
                .or(() -> tenantRepository.findBySlug(normalized))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found: " + normalized));
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new BadRequestException("Tenant key is required");
        }
        return tenantKey.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Subscription plan code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String currentActor() {
        try {
            return securityUtils.getCurrentUserEmailOrThrow();
        } catch (ForbiddenOperationException ignored) {
            return SYSTEM_ACTOR;
        }
    }

    private String normalizeActor(String actor, SubscriptionChangeSource source) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim().toLowerCase(Locale.ROOT);
        }
        return source == SubscriptionChangeSource.MANUAL ? currentActor() : SYSTEM_ACTOR;
    }

    private String displayName(FeatureKey featureKey) {
        return Arrays.stream(featureKey.name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private void applySeedMetadata(SubscriptionPlan plan, SubscriptionPlanSeed seed) {
        plan.setDescription(seed.description());
        plan.setMonthlyPrice(seed.monthlyPrice());
        plan.setYearlyPrice(seed.yearlyPrice());
        plan.setBillingPeriod(seed.billingPeriod());
        plan.setBadge(seed.badge());
        plan.setRecommended(seed.recommended());
        plan.setColor(seed.color());
        plan.setIcon(seed.icon());
        plan.setDisplayOrder(seed.displayOrder());
    }

    private Set<FeatureKey> seedFeatures(String planCode) {
        return switch (normalizeCode(planCode)) {
            case FREE_PLAN_CODE -> Set.of(
                    FeatureKey.DASHBOARD,
                    FeatureKey.EMPLOYEE,
                    FeatureKey.PROJECTS,
                    FeatureKey.TASKS,
                    FeatureKey.NOTIFICATIONS,
                    FeatureKey.SETTINGS);
            case "STARTER" -> Set.of(
                    FeatureKey.DASHBOARD,
                    FeatureKey.EMPLOYEE,
                    FeatureKey.TEAMS,
                    FeatureKey.PROJECTS,
                    FeatureKey.TASKS,
                    FeatureKey.ATTENDANCE,
                    FeatureKey.LEAVE,
                    FeatureKey.NOTIFICATIONS,
                    FeatureKey.SETTINGS);
            case "PROFESSIONAL" -> Set.of(
                    FeatureKey.DASHBOARD,
                    FeatureKey.EMPLOYEE,
                    FeatureKey.TEAMS,
                    FeatureKey.PROJECTS,
                    FeatureKey.TASKS,
                    FeatureKey.ATTENDANCE,
                    FeatureKey.LEAVE,
                    FeatureKey.RECRUITMENT,
                    FeatureKey.REPORTS,
                    FeatureKey.NOTIFICATIONS,
                    FeatureKey.CHAT,
                    FeatureKey.ANALYTICS,
                    FeatureKey.ANNOUNCEMENTS,
                    FeatureKey.SETTINGS);
            default -> Set.of(FeatureKey.values());
        };
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SubscriptionPlanSeed(
            String name,
            String code,
            String description,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice,
            String billingPeriod,
            String badge,
            boolean recommended,
            String color,
            String icon,
            int displayOrder,
            Set<FeatureKey> features) {
    }
}
