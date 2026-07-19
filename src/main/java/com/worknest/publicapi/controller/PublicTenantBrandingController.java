package com.worknest.publicapi.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.common.web.BrandingHttpSupport;
import com.worknest.common.storage.AssetObservability;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenantSlug}/branding")
public class PublicTenantBrandingController {

    private final TenantBrandingService tenantBrandingService;
    private final AssetObservability observability;

    public PublicTenantBrandingController(
            TenantBrandingService tenantBrandingService,
            AssetObservability observability) {
        this.tenantBrandingService = tenantBrandingService;
        this.observability = observability;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> getBranding(
            @PathVariable("tenantSlug") String tenantSlug,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        TenantBrandingViewDto branding = tenantBrandingService.getPublicBranding(tenantSlug);
        if (BrandingHttpSupport.matches(ifNoneMatch, branding.brandingVersion())) {
            observability.recordBrandingCacheHit("public");
            return ResponseEntity.status(304)
                    .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, stale-while-revalidate=300")
                .body(ApiResponse.success("Tenant branding retrieved successfully", branding));
    }
}
