package com.worknest.master.service;

import com.worknest.tenant.entity.Employee;
import com.worknest.master.entity.PlatformUser;

public interface PlatformUserSyncService {

    PlatformUser syncOnCreate(Employee employee, String rawPassword, String tenantKey);

    PlatformUser syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey);

    PlatformUser syncStatus(Employee employee, String tenantKey);

    PlatformUser provisionEmployeeAccount(Employee employee, String rawPassword, String tenantKey);
}
