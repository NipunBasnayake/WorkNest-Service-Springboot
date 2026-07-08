package com.worknest.multitenancy.context;

public final class TenantContextHolder {

    private static final ThreadLocal<String> CURRENT_TENANT_SLUG = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_KEY = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantSlug(String tenantSlug) {
        CURRENT_TENANT_SLUG.set(normalizeTenantSlug(tenantSlug));
    }

    public static String getTenantSlug() {
        return CURRENT_TENANT_SLUG.get();
    }

    public static void setTenantKey(String tenantKey) {
        CURRENT_TENANT_KEY.set(normalizeTenantIdentifier(tenantKey));
    }

    public static String getTenantKey() {
        return CURRENT_TENANT_KEY.get();
    }

    public static void clear() {
        CURRENT_TENANT_SLUG.remove();
        CURRENT_TENANT_KEY.remove();
    }

    private static String normalizeTenantIdentifier(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            return null;
        }
        String normalized = tenantIdentifier.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeTenantSlug(String tenantSlug) {
        return normalizeTenantIdentifier(tenantSlug);
    }
}
