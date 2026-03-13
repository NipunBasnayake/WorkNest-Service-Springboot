package com.worknest.tenant.resolver;

import com.worknest.common.util.AppConstants;
import com.worknest.tenant.context.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {

    private static final Logger logger = LoggerFactory.getLogger(CurrentTenantIdentifierResolverImpl.class);
    private final String defaultTenant;

    public CurrentTenantIdentifierResolverImpl(
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenantId();

        if (tenantId == null || tenantId.trim().isEmpty()) {
            logger.debug("No tenant context found, using default tenant: {}", defaultTenant);
            return defaultTenant;
        }

        logger.debug("Resolved tenant identifier: {}", tenantId);
        return tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Allow existing sessions without validation
        return true;
    }
}

