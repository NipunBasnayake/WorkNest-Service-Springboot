package com.worknest.tenant.service.impl;

import com.worknest.master.service.PlatformUserSyncService;
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
    public void syncOnCreate(Employee employee, String rawPassword, String tenantKey) {
        platformUserSyncService.syncOnCreate(employee, rawPassword, tenantKey);
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public void syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey) {
        platformUserSyncService.syncOnUpdate(employee, oldEmail, rawPassword, tenantKey);
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.NOT_SUPPORTED)
    public void syncStatus(Employee employee, String tenantKey) {
        platformUserSyncService.syncStatus(employee, tenantKey);
    }
}
