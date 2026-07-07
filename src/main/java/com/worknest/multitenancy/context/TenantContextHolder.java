package com.worknest.multitenancy.context;

public final class TenantContextHolder {

    private static final ThreadLocal<String> CURRENT_TENANT_SLUG = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantSlug(String tenantSlug) {
        CURRENT_TENANT_SLUG.set(normalizeTenantSlug(tenantSlug));
    }

    public static String getTenantSlug() {
        return CURRENT_TENANT_SLUG.get();
    }

    public static void clear() {
        CURRENT_TENANT_SLUG.remove();
    }

    private static String normalizeTenantSlug(String tenantSlug) {
        if (tenantSlug == null) {
            return null;
        }
        String normalized = tenantSlug.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}