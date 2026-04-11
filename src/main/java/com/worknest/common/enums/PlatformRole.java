package com.worknest.common.enums;

public enum PlatformRole {
    PLATFORM_ADMIN,
    TENANT_ADMIN,
    ADMIN,
    MANAGER,
    HR,
    EMPLOYEE;

    public boolean isTenantScoped() {
        return this != PLATFORM_ADMIN;
    }

    public boolean isPlatformAdmin() {
        return this == PLATFORM_ADMIN;
    }

    public boolean isTenantAdminEquivalent() {
        return this == TENANT_ADMIN || this == ADMIN;
    }

    public boolean isHrEquivalent() {
        return this == HR;
    }

    public boolean isLegacyManager() {
        return this == MANAGER;
    }

    public boolean isEmployeeOnly() {
        return this == EMPLOYEE;
    }

    public boolean isTenantBusinessRole() {
        return isTenantScoped() && !isPlatformAdmin();
    }

    public boolean canManageTenantSettings() {
        return isTenantAdminEquivalent();
    }
}
