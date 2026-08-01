package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.subscription.CurrentSubscriptionAccessDto;
import com.worknest.master.dto.subscription.TenantPackageCatalogDto;
import com.worknest.master.dto.subscription.TenantPlanAssignmentRequestDto;
import com.worknest.master.dto.subscription.TenantSubscriptionResponseDto;
import com.worknest.master.service.SubscriptionService;
import com.worknest.security.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{tenantSlug}/subscription")
public class TenantSubscriptionAccessController {

    private final SubscriptionService subscriptionService;
    private final SecurityUtils securityUtils;

    public TenantSubscriptionAccessController(
            SubscriptionService subscriptionService,
            SecurityUtils securityUtils) {
        this.subscriptionService = subscriptionService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CurrentSubscriptionAccessDto>> getCurrentSubscription(
            @PathVariable String tenantSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Current subscription access retrieved successfully",
                subscriptionService.getCurrentAccess(securityUtils.getCurrentTenantKeyOrThrow())));
    }

    @GetMapping("/packages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantPackageCatalogDto>> getPackages(
            @PathVariable String tenantSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription packages retrieved successfully",
                subscriptionService.getTenantPackageCatalog(securityUtils.getCurrentTenantKeyOrThrow())));
    }

    @PutMapping("/package")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantSubscriptionResponseDto>> selectPackage(
            @PathVariable String tenantSlug,
            @Valid @RequestBody TenantPlanAssignmentRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription package selected successfully",
                subscriptionService.selectTenantPackage(
                        securityUtils.getCurrentTenantKeyOrThrow(),
                        request.planCode())));
    }
}
