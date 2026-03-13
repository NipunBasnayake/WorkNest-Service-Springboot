package com.worknest.common.util;

public class AppConstants {

    // Tenant related constants
    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String TENANT_STATUS_ACTIVE = "ACTIVE";
    public static final String TENANT_STATUS_INACTIVE = "INACTIVE";
    public static final String TENANT_STATUS_SUSPENDED = "SUSPENDED";

    // Default tenant identifier for master database
    public static final String DEFAULT_TENANT = "master";

    // Employee status constants
    public static final String EMPLOYEE_STATUS_ACTIVE = "ACTIVE";
    public static final String EMPLOYEE_STATUS_INACTIVE = "INACTIVE";

    private AppConstants() {
        // Private constructor to prevent instantiation
    }
}

