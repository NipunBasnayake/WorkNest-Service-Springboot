package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.common.web.BrandingHttpSupport;
import com.worknest.common.storage.AssetObservability;
import com.worknest.master.dto.BrandingUpdateRequestDto;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/{tenantSlug}")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
public class TenantBrandingController {

    private final TenantBrandingService tenantBrandingService;
    private final AssetObservability observability;

    public TenantBrandingController(
            TenantBrandingService tenantBrandingService,
            AssetObservability observability) {
        this.tenantBrandingService = tenantBrandingService;
        this.observability = observability;
    }

    @GetMapping("/branding")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> getBranding(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return readResponse(tenantBrandingService.getCurrentTenantBranding(), ifNoneMatch);
    }

    @GetMapping("/settings/branding")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> getEditableBranding(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return readResponse(tenantBrandingService.getCurrentTenantBranding(), ifNoneMatch);
    }

    @PatchMapping("/settings/branding")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> updateBranding(
            @Valid @RequestBody BrandingUpdateRequestDto request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        TenantBrandingViewDto branding = tenantBrandingService.updateCurrentTenantBranding(
                request,
                BrandingHttpSupport.parseVersion(ifMatch)
        );
        return writeResponse("Tenant branding updated successfully", branding);
    }

    @PutMapping(value = "/settings/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> uploadLogo(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        TenantBrandingViewDto branding = tenantBrandingService.uploadCurrentTenantLogo(
                file,
                BrandingHttpSupport.parseVersion(ifMatch)
        );
        return writeResponse("Company logo updated successfully", branding);
    }

    @DeleteMapping("/settings/branding/logo")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantBrandingViewDto>> deleteLogo(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        TenantBrandingViewDto branding = tenantBrandingService.deleteCurrentTenantLogo(
                BrandingHttpSupport.parseVersion(ifMatch)
        );
        return writeResponse("Company logo removed successfully", branding);
    }

    private ResponseEntity<ApiResponse<TenantBrandingViewDto>> readResponse(
            TenantBrandingViewDto branding,
            String ifNoneMatch) {
        if (BrandingHttpSupport.matches(ifNoneMatch, branding.brandingVersion())) {
            observability.recordBrandingCacheHit("tenant");
            return ResponseEntity.status(304)
                    .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60, stale-while-revalidate=300")
                .body(ApiResponse.success("Tenant branding retrieved successfully", branding));
    }

    private ResponseEntity<ApiResponse<TenantBrandingViewDto>> writeResponse(
            String message,
            TenantBrandingViewDto branding) {
        return ResponseEntity.ok()
                .eTag(BrandingHttpSupport.etag(branding.brandingVersion()))
                .body(ApiResponse.success(message, branding));
    }
}
