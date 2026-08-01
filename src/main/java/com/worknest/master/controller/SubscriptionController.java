package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.subscription.FeatureMatrixResponseDto;
import com.worknest.master.dto.subscription.FeatureToggleRequestDto;
import com.worknest.master.dto.subscription.PlanActiveRequestDto;
import com.worknest.master.dto.subscription.SubscriptionFeatureResponseDto;
import com.worknest.master.dto.subscription.SubscriptionOverviewDto;
import com.worknest.master.dto.subscription.SubscriptionPlanRequestDto;
import com.worknest.master.dto.subscription.SubscriptionPlanResponseDto;
import com.worknest.master.dto.subscription.SubscriptionStatisticsDto;
import com.worknest.master.dto.subscription.TenantPlanAssignmentRequestDto;
import com.worknest.master.dto.subscription.TenantSubscriptionResponseDto;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/subscriptions")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<SubscriptionOverviewDto>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription overview retrieved successfully",
                subscriptionService.getOverview()));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<SubscriptionStatisticsDto>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription statistics retrieved successfully",
                subscriptionService.getStatistics()));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponseDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription plans retrieved successfully",
                subscriptionService.getPlans()));
    }

    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDto>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Subscription plan created successfully",
                subscriptionService.createPlan(request)));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDto>> updatePlan(
            @PathVariable Long planId,
            @Valid @RequestBody SubscriptionPlanRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription plan updated successfully",
                subscriptionService.updatePlan(planId, request)));
    }

    @PatchMapping("/plans/{planId}/active")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponseDto>> setPlanActive(
            @PathVariable Long planId,
            @Valid @RequestBody PlanActiveRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription plan status updated successfully",
                subscriptionService.setPlanActive(planId, request.active())));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long planId) {
        subscriptionService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription plan deleted successfully",
                null));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<List<SubscriptionFeatureResponseDto>>> getFeatures() {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription features retrieved successfully",
                subscriptionService.getFeatures()));
    }

    @GetMapping("/feature-matrix")
    public ResponseEntity<ApiResponse<FeatureMatrixResponseDto>> getFeatureMatrix() {
        return ResponseEntity.ok(ApiResponse.success(
                "Feature matrix retrieved successfully",
                subscriptionService.getFeatureMatrix()));
    }

    @PatchMapping("/plans/{planId}/features/{featureKey}")
    public ResponseEntity<ApiResponse<FeatureMatrixResponseDto>> setPlanFeature(
            @PathVariable Long planId,
            @PathVariable FeatureKey featureKey,
            @Valid @RequestBody FeatureToggleRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Plan feature updated successfully",
                subscriptionService.setPlanFeature(planId, featureKey, request.enabled())));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<TenantSubscriptionResponseDto>>> getTenantSubscriptions() {
        return ResponseEntity.ok(ApiResponse.success(
                "Tenant subscriptions retrieved successfully",
                subscriptionService.getTenantSubscriptions()));
    }

    @PutMapping("/tenants/{tenantKey}")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponseDto>> assignTenantPlan(
            @PathVariable String tenantKey,
            @Valid @RequestBody TenantPlanAssignmentRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tenant subscription updated successfully",
                subscriptionService.assignTenantPlan(tenantKey, request)));
    }

    @PatchMapping("/tenants/{tenantKey}/deactivate")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponseDto>> deactivateTenantSubscription(
            @PathVariable String tenantKey) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tenant subscription deactivated successfully",
                subscriptionService.deactivateTenantSubscription(tenantKey)));
    }
}
