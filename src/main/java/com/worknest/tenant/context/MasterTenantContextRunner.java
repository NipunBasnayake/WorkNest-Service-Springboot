package com.worknest.tenant.context;

import com.worknest.common.util.AppConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class MasterTenantContextRunner {

    private final String defaultTenant;

    public MasterTenantContextRunner(
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public <T> T runInMasterContext(Supplier<T> supplier) {
        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(defaultTenant);
            return supplier.get();
        } finally {
            restoreTenant(previousTenant);
        }
    }

    public void runInMasterContext(Runnable runnable) {
        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(defaultTenant);
            runnable.run();
        } finally {
            restoreTenant(previousTenant);
        }
    }

    private void restoreTenant(String previousTenant) {
        if (previousTenant == null || previousTenant.isBlank()) {
            TenantContext.clear();
            return;
        }
        TenantContext.setTenantId(previousTenant);
    }
}
