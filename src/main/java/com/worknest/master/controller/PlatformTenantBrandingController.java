package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.common.web.BrandingHttpSupport;
import com.worknest.common.storage.AssetObservability;
import com.worknest.master.dto.BrandingUpdateRequestDto;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants/{tenantKey}/branding")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantBrandingController {

    private final TenantBrandingService tenantBrandingService;
    private final AssetObservability observability;

    public PlatformTenantBrandingController(
            TenantBrandingService tenantBrandingService,
            AssetObservability observability) {
        this.tenantBrandingService = tenantBrandingService;
        this.observability = observability;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> getBranding(
            @PathVariable("tenantKey") String tenantKey,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        TenantBrandingViewDto branding = tenantBrandingService.getPlatformBranding(tenantKey);
        if (BrandingHttpSupport.matches(ifNoneMatch, branding.brandingVersion())) {
            observability.recordBrandingCacheHit("platform");
            return ResponseEntity.status(304)
                    .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(ApiResponse.success("Tenant branding retrieved successfully", branding));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> updateBranding(
            @PathVariable("tenantKey") String tenantKey,
            @Valid @RequestBody BrandingUpdateRequestDto request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return writeResponse("Tenant branding updated successfully", tenantBrandingService.updatePlatformBranding(
                tenantKey,
                request,
                BrandingHttpSupport.parseVersion(ifMatch)
        ));
    }

    private ResponseEntity<ApiResponse<TenantBrandingViewDto>> writeResponse(
            String message,
            TenantBrandingViewDto branding) {
        return ResponseEntity.ok()
                .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                .body(ApiResponse.success(message, branding));
    }
}
