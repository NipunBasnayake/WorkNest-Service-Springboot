package com.worknest.master.service;

import com.worknest.master.entity.PlatformTenant;

import java.util.Optional;

public interface MasterTenantLookupService {

    Optional<PlatformTenant> findByTenantKey(String tenantKey);
}

