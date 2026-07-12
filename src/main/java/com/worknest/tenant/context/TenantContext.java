package com.worknest.tenant.context;

import com.worknest.multitenancy.context.TenantContextHolder;

public class TenantContext {

    public static void setTenantId(String tenantId) {
        TenantContextHolder.setTenantKey(tenantId);
        TenantContextHolder.setTenantSlug(tenantId);
    }

    public static String getTenantId() {
        String tenantKey = TenantContextHolder.getTenantKey();
        return tenantKey == null ? TenantContextHolder.getTenantSlug() : tenantKey;
    }

    public static void clear() {
        TenantContextHolder.clear();
    }

    private TenantContext() {
        // Private constructor to prevent instantiation
    }
}

