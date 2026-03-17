package com.worknest.tenant.service.impl;

import com.worknest.master.service.PlatformUserSyncService;
import com.worknest.master.entity.PlatformUser;
import com.worknest.tenant.entity.Employee;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserSyncBridgeService {

    private final PlatformUserSyncService platformUserSyncService;

    public PlatformUserSyncBridgeService(PlatformUserSyncService platformUserSyncService) {
        this.platformUserSyncService = platformUserSyncService;
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public PlatformUser syncOnCreate(Employee employee, String rawPassword, String tenantKey) {
        return platformUserSyncService.syncOnCreate(employee, rawPassword, tenantKey);
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public PlatformUser syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey) {
        return platformUserSyncService.syncOnUpdate(employee, oldEmail, rawPassword, tenantKey);
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public PlatformUser syncStatus(Employee employee, String tenantKey) {
        return platformUserSyncService.syncStatus(employee, tenantKey);
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public PlatformUser provisionEmployeeAccount(Employee employee, String rawPassword, String tenantKey) {
        return platformUserSyncService.provisionEmployeeAccount(employee, rawPassword, tenantKey);
    }
}
