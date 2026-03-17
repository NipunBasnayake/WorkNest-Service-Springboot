package com.worknest.config;

import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.service.TenantSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(20)
public class TenantSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaInitializer.class);

    private final PlatformTenantRepository platformTenantRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final TenantSchemaService tenantSchemaService;

    public TenantSchemaInitializer(
            PlatformTenantRepository platformTenantRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            TenantSchemaService tenantSchemaService) {
        this.platformTenantRepository = platformTenantRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Override
    public void run(String... args) {
        masterTenantContextRunner.runInMasterContext(() -> {
            List<PlatformTenant> tenants = platformTenantRepository.findAll();
            for (PlatformTenant tenant : tenants) {
                if (tenant.getStatus() == TenantStatus.ACTIVE) {
                    try {
                        tenantSchemaService.ensureTenantSchema(tenant);
                        log.info("Tenant schema ensured for tenant {}", tenant.getTenantKey());
                    } catch (Exception ex) {
                        log.error("Tenant schema initialization failed for tenant {}. Continuing startup.",
                                tenant.getTenantKey(), ex);
                    }
                }
            }
        });
    }
}
