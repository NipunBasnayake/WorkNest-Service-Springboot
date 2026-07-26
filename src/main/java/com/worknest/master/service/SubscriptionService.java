package com.worknest.master.service;

import com.worknest.master.dto.subscription.CurrentSubscriptionAccessDto;
import com.worknest.master.dto.subscription.FeatureMatrixResponseDto;
import com.worknest.master.dto.subscription.SubscriptionFeatureResponseDto;
import com.worknest.master.dto.subscription.SubscriptionOverviewDto;
import com.worknest.master.dto.subscription.SubscriptionPlanRequestDto;
import com.worknest.master.dto.subscription.SubscriptionPlanResponseDto;
import com.worknest.master.dto.subscription.SubscriptionStatisticsDto;
import com.worknest.master.dto.subscription.TenantPlanAssignmentRequestDto;
import com.worknest.master.dto.subscription.TenantSubscriptionResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.enums.SubscriptionChangeSource;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionService {
    SubscriptionOverviewDto getOverview();

    List<SubscriptionPlanResponseDto> getPlans();

    SubscriptionPlanResponseDto createPlan(SubscriptionPlanRequestDto request);

    SubscriptionPlanResponseDto updatePlan(Long planId, SubscriptionPlanRequestDto request);

    SubscriptionPlanResponseDto setPlanActive(Long planId, boolean active);

    List<SubscriptionFeatureResponseDto> getFeatures();

    FeatureMatrixResponseDto getFeatureMatrix();

    FeatureMatrixResponseDto setPlanFeature(Long planId, FeatureKey featureKey, boolean enabled);

    List<TenantSubscriptionResponseDto> getTenantSubscriptions();

    TenantSubscriptionResponseDto assignTenantPlan(
            String tenantKey,
            TenantPlanAssignmentRequestDto request);

    TenantSubscriptionResponseDto changeTenantPlan(
            String tenantKey,
            String planCode,
            LocalDateTime expiresAt,
            SubscriptionChangeSource source,
            String actor);

    TenantSubscriptionResponseDto deactivateTenantSubscription(String tenantKey);

    CurrentSubscriptionAccessDto getCurrentAccess(String tenantKey);

    SubscriptionStatisticsDto getStatistics();

    void assignDefaultPlan(PlatformTenant tenant);

    void bootstrapDefaults();
}
