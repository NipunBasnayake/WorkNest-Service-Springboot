package com.worknest.master.controller;

import com.worknest.master.service.TenantBrandingService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
public class BrandingAssetController {

    private final TenantBrandingService tenantBrandingService;

    public BrandingAssetController(TenantBrandingService tenantBrandingService) {
        this.tenantBrandingService = tenantBrandingService;
    }

    @GetMapping("/api/public/{tenantSlug}/branding/assets/{publicId}/{variant}")
    public ResponseEntity<Resource> getPublicAsset(
            @PathVariable("tenantSlug") String tenantSlug,
            @PathVariable("publicId") String publicId,
            @PathVariable("variant") String variant,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH,
                    required = false) String ifNoneMatch) {
        TenantBrandingService.BrandingResource resource = tenantBrandingService
                .getPublicLogoResource(tenantSlug, publicId, variant);
        return resourceResponse(resource, ifNoneMatch, "public, max-age=31536000, immutable");
    }

    @GetMapping("/api/platform/tenants/{tenantKey}/branding/assets/{publicId}/{variant}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Resource> getPlatformAsset(
            @PathVariable("tenantKey") String tenantKey,
            @PathVariable("publicId") String publicId,
            @PathVariable("variant") String variant,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH,
                    required = false) String ifNoneMatch) {
        TenantBrandingService.BrandingResource resource = tenantBrandingService
                .getPlatformLogoResource(tenantKey, publicId, variant);
        return resourceResponse(resource, ifNoneMatch, "private, max-age=31536000, immutable");
    }

    private ResponseEntity<Resource> resourceResponse(
            TenantBrandingService.BrandingResource resource,
            String ifNoneMatch,
            String cacheControl) {
        if (resource.etag().equals(ifNoneMatch) || ("W/" + resource.etag()).equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(resource.etag()).build();
        }
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(resource.contentType());
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .eTag(resource.etag())
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''"
                        + UriUtils.encode(resource.filename(), StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(contentType)
                .body(resource.resource());
    }
}
