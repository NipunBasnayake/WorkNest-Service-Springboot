package com.worknest.tenant.service;

import com.worknest.master.entity.PlatformTenant;

public interface TenantSchemaService {

    void ensureTenantSchema(PlatformTenant tenant);
}
