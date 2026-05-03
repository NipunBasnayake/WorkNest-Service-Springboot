package com.worknest.config;

import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.service.impl.TenantAdminEmployeeMirrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(40)
public class TenantAdminMirrorRepairInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminMirrorRepairInitializer.class);

    private final PlatformTenantRepository platformTenantRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService;

    public TenantAdminMirrorRepairInitializer(
            PlatformTenantRepository platformTenantRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService) {
        this.platformTenantRepository = platformTenantRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.tenantAdminEmployeeMirrorService = tenantAdminEmployeeMirrorService;
    }

    @Override
    public void run(String... args) {
        List<PlatformTenant> activeTenants = masterTenantContextRunner.runInMasterContext(() ->
                platformTenantRepository.findAll().stream()
                        .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                        .toList());

        for (PlatformTenant tenant : activeTenants) {
            try {
                tenantAdminEmployeeMirrorService.ensureTenantAdminEmployeeMirror(tenant.getTenantKey());
            } catch (Exception ex) {
                log.warn("Tenant admin mirror repair skipped for tenant {}", tenant.getTenantKey(), ex);
            }
        }
    }
}