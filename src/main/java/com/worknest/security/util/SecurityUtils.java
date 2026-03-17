package com.worknest.security.util;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.security.model.PlatformUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public PlatformUserPrincipal getCurrentPrincipalOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserPrincipal principal)) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        return principal;
    }

    public String getCurrentTenantKeyOrThrow() {
        String tenantKey = getCurrentPrincipalOrThrow().getTenantKey();
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new ForbiddenOperationException("Tenant key is not available for current user");
        }
        return tenantKey.trim().toLowerCase();
    }

    public String getCurrentUserEmailOrThrow() {
        return getCurrentPrincipalOrThrow().getUsername();
    }

    public PlatformRole getCurrentRoleOrThrow() {
        return getCurrentPrincipalOrThrow().getRole();
    }

    public boolean isCurrentUser(Long platformUserId) {
        return getCurrentPrincipalOrThrow().getId().equals(platformUserId);
    }
}
