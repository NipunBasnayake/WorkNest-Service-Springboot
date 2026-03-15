package com.worknest.master.service;

import com.worknest.tenant.entity.Employee;

public interface PlatformUserSyncService {

    void syncOnCreate(Employee employee, String rawPassword, String tenantKey);

    void syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey);

    void syncStatus(Employee employee, String tenantKey);
}
