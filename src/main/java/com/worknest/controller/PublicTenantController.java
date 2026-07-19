package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenantSlug}")
public class PublicTenantController {

    private final TenantBrandingService tenantBrandingService;

    public PublicTenantController(TenantBrandingService tenantBrandingService) {
        this.tenantBrandingService = tenantBrandingService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> getTenantBootstrapInfo(
            @PathVariable("tenantSlug") String tenantSlug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tenant metadata retrieved successfully",
                tenantBrandingService.getPublicBranding(tenantSlug)
        ));
    }
}
