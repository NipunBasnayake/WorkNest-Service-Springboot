package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.subscription.CurrentSubscriptionAccessDto;
import com.worknest.master.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{tenantSlug}/subscription")
public class TenantSubscriptionAccessController {

    private final SubscriptionService subscriptionService;

    public TenantSubscriptionAccessController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CurrentSubscriptionAccessDto>> getCurrentSubscription(
            @PathVariable String tenantSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Current subscription access retrieved successfully",
                subscriptionService.getCurrentAccess(tenantSlug)));
    }
}
