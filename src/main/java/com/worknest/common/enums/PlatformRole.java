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
}
