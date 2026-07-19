package com.worknest.notification.email;

import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.multitenancy.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BrandContextResolver {

    private static final Logger log = LoggerFactory.getLogger(BrandContextResolver.class);

    private final TenantBrandingService tenantBrandingService;
    private final String publicApiBaseUrl;

    public BrandContextResolver(
            TenantBrandingService tenantBrandingService,
            @Value("${app.public-api-base-url:http://localhost:8080}") String publicApiBaseUrl) {
        this.tenantBrandingService = tenantBrandingService;
        this.publicApiBaseUrl = publicApiBaseUrl == null ? "" : publicApiBaseUrl.replaceAll("/+$", "");
    }

    public BrandContext resolveCurrentTenantOrDefault() {
        String tenantSlug = firstNonBlank(TenantContextHolder.getTenantSlug(), TenantContextHolder.getTenantKey());
        if (tenantSlug == null) return BrandContext.workNest();

        try {
            TenantBrandingViewDto branding = tenantBrandingService.getPublicBranding(tenantSlug);
            String logoUrl = branding.logo() == null ? null : firstNonBlank(
                    branding.logo().variants() == null ? null : branding.logo().variants().get("256"),
                    branding.logo().url()
            );
            return new BrandContext(
                    firstNonBlank(branding.companyName(), "WorkNest"),
                    validColorOrDefault(branding.primaryColor()),
                    absolutePublicUrl(logoUrl),
                    true
            );
        } catch (RuntimeException exception) {
            log.debug("Tenant email branding unavailable for {}; using WorkNest fallback", tenantSlug, exception);
            return BrandContext.workNest();
        }
    }

    private String absolutePublicUrl(String value) {
        String url = firstNonBlank(value);
        if (url == null) return null;
        if (url.startsWith("https://") || url.startsWith("http://")) return url;
        return publicApiBaseUrl + (url.startsWith("/") ? url : "/" + url);
    }

    private String validColorOrDefault(String value) {
        return value != null && value.matches("^#[0-9A-Fa-f]{6}$") ? value.toUpperCase() : "#9332EA";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
