package com.worknest.tenant.resolver;

import com.worknest.common.util.AppConstants;
import com.worknest.common.exception.TenantContextMissingException;
import com.worknest.tenant.context.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {

    private static final Logger logger = LoggerFactory.getLogger(CurrentTenantIdentifierResolverImpl.class);
    private static final String DEFAULT_TENANT = AppConstants.DEFAULT_TENANT;
    private static final String BOOTSTRAP_TENANT = "BOOTSTRAP";

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            String asyncTenantId = normalizeTenantId(TenantContext.getTenantId());
            if (asyncTenantId != null && !DEFAULT_TENANT.equalsIgnoreCase(asyncTenantId)) {
                logger.debug("Resolved async tenant identifier: {}", asyncTenantId);
                return asyncTenantId;
            }
            logger.debug("No active request context detected, using bootstrap tenant identifier");
            return BOOTSTRAP_TENANT;
        }

        String tenantId = normalizeTenantId(TenantContext.getTenantId());

        if (tenantId == null) {
            throw new TenantContextMissingException(
                    "Tenant context is missing for tenant-scoped database access");
        }

        logger.debug("Resolved tenant identifier: {}", tenantId);
        if (DEFAULT_TENANT.equalsIgnoreCase(tenantId)) {
            throw new TenantContextMissingException(
                    "Master tenant identifier is not allowed for tenant-scoped persistence access");
        }

        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Allow existing sessions without validation
        return true;
    }

    private String normalizeTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}

