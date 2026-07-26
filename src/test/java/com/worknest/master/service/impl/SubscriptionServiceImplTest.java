package com.worknest.master.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.SubscriptionAuditLog;
import com.worknest.master.entity.SubscriptionPlan;
import com.worknest.master.entity.TenantSubscription;
import com.worknest.master.enums.SubscriptionAuditAction;
import com.worknest.master.enums.SubscriptionChangeSource;
import com.worknest.master.repository.PlanFeatureRepository;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.SubscriptionAuditLogRepository;
import com.worknest.master.repository.SubscriptionFeatureRepository;
import com.worknest.master.repository.SubscriptionRepository;
import com.worknest.master.repository.TenantSubscriptionRepository;
import com.worknest.security.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionFeatureRepository featureRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private SubscriptionAuditLogRepository auditRepository;
    @Mock private PlatformTenantRepository tenantRepository;
    @Mock private SecurityUtils securityUtils;

    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionServiceImpl(
                subscriptionRepository,
                featureRepository,
                planFeatureRepository,
                tenantSubscriptionRepository,
                auditRepository,
                tenantRepository,
                securityUtils,
                CLOCK);
    }

    @Test
    void assignsFreePlanAutomaticallyWithUnlimitedDuration() {
        PlatformTenant tenant = tenant(7L, "acme", "Acme");
        SubscriptionPlan free = plan(1L, "Free", "FREE", 10, true);
        when(tenantSubscriptionRepository.existsByTenantId(7L)).thenReturn(false);
        when(subscriptionRepository.findByCodeIgnoreCase("FREE")).thenReturn(Optional.of(free));

        service.assignDefaultPlan(tenant);

        ArgumentCaptor<TenantSubscription> subscriptionCaptor =
                ArgumentCaptor.forClass(TenantSubscription.class);
        verify(tenantSubscriptionRepository).save(subscriptionCaptor.capture());
        TenantSubscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getTenant()).isSameAs(tenant);
        assertThat(saved.getSubscriptionPlan()).isSameAs(free);
        assertThat(saved.getAssignedDate()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.isActive()).isTrue();

        ArgumentCaptor<SubscriptionAuditLog> auditCaptor =
                ArgumentCaptor.forClass(SubscriptionAuditLog.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(SubscriptionAuditAction.ASSIGNED);
        assertThat(auditCaptor.getValue().getSource()).isEqualTo(SubscriptionChangeSource.SYSTEM);
    }

    @Test
    void doesNotReplaceAnExistingTenantSubscription() {
        PlatformTenant tenant = tenant(7L, "acme", "Acme");
        when(tenantSubscriptionRepository.existsByTenantId(7L)).thenReturn(true);

        service.assignDefaultPlan(tenant);

        verify(subscriptionRepository, never()).findByCodeIgnoreCase(any());
        verify(tenantSubscriptionRepository, never()).save(any());
    }

    @Test
    void changeTenantPlanRecordsAnUpgradeWithoutPaymentLogic() {
        PlatformTenant tenant = tenant(7L, "acme", "Acme");
        SubscriptionPlan free = plan(1L, "Free", "FREE", 10, true);
        SubscriptionPlan professional = plan(3L, "Professional", "PROFESSIONAL", 30, true);
        TenantSubscription current = new TenantSubscription();
        current.setId(12L);
        current.setTenant(tenant);
        current.setSubscriptionPlan(free);
        current.setAssignedDate(LocalDateTime.now(CLOCK).minusDays(10));
        current.setActive(true);

        when(tenantRepository.findByTenantKey("acme")).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findByCodeIgnoreCase("PROFESSIONAL"))
                .thenReturn(Optional.of(professional));
        when(tenantSubscriptionRepository.findByTenantId(7L)).thenReturn(Optional.of(current));
        when(tenantSubscriptionRepository.saveAndFlush(any(TenantSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.changeTenantPlan(
                "acme",
                "PROFESSIONAL",
                null,
                SubscriptionChangeSource.PAYMENT,
                "billing-adapter@worknest.local");

        assertThat(response.planCode()).isEqualTo("PROFESSIONAL");
        assertThat(response.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<SubscriptionAuditLog> auditCaptor =
                ArgumentCaptor.forClass(SubscriptionAuditLog.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(SubscriptionAuditAction.UPGRADED);
        assertThat(auditCaptor.getValue().getSource()).isEqualTo(SubscriptionChangeSource.PAYMENT);
        assertThat(auditCaptor.getValue().getPreviousPlanCode()).isEqualTo("FREE");
        assertThat(auditCaptor.getValue().getNewPlanCode()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void freePlanCannotBeDisabledBecauseItIsTheRegistrationDefault() {
        SubscriptionPlan free = plan(1L, "Free", "FREE", 10, true);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service.setPlanActive(1L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FREE plan must remain active");
    }

    private PlatformTenant tenant(Long id, String tenantKey, String companyName) {
        PlatformTenant tenant = new PlatformTenant();
        tenant.setId(id);
        tenant.setTenantKey(tenantKey);
        tenant.setCompanyName(companyName);
        return tenant;
    }

    private SubscriptionPlan plan(
            Long id,
            String name,
            String code,
            int displayOrder,
            boolean active) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setCode(code);
        plan.setDisplayOrder(displayOrder);
        plan.setActive(active);
        return plan;
    }
}
