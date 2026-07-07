package com.worknest.tenant.context;

import com.worknest.multitenancy.context.TenantContextHolder;

public class TenantContext {

    public static void setTenantId(String tenantId) {
        TenantContextHolder.setTenantSlug(tenantId);
    }

    public static String getTenantId() {
        return TenantContextHolder.getTenantSlug();
    }

    public static void clear() {
        TenantContextHolder.clear();
    }

    private TenantContext() {
        // Private constructor to prevent instantiation
    }
}

