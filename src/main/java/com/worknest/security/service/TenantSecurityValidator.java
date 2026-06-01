package com.worknest.security.service;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.multitenancy.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(TenantSecurityValidator.class);

    public void validateTenantSecurity(String jwtTenant, String urlTenant) {
        String resolvedTenant = TenantContextHolder.getTenantSlug();

        if (urlTenant != null) {
            // Check urlTenant matches resolvedTenant
            if (resolvedTenant == null || !resolvedTenant.equalsIgnoreCase(urlTenant)) {
                log.warn("[TENANT] Mismatch: resolvedTenant={} != urlTenant={}", resolvedTenant, urlTenant);
                throw new ForbiddenOperationException("Resolved tenant does not match URL tenant");
            }

            // Check jwtTenant matches urlTenant
            if (jwtTenant != null && !jwtTenant.equalsIgnoreCase(urlTenant)) {
                log.warn("[TENANT] Mismatch: jwtTenant={} != urlTenant={}", jwtTenant, urlTenant);
                throw new ForbiddenOperationException("JWT tenant does not match URL tenant");
            }

            // Check jwtTenant matches resolvedTenant
            if (jwtTenant != null && !jwtTenant.equalsIgnoreCase(resolvedTenant)) {
                log.warn("[TENANT] Mismatch: jwtTenant={} != resolvedTenant={}", jwtTenant, resolvedTenant);
                throw new ForbiddenOperationException("JWT tenant does not match resolved tenant");
            }
        }
    }
}
