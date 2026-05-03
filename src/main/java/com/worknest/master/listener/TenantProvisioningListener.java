package com.worknest.master.listener;

import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.event.TenantProvisioningRequestedEvent;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.service.impl.TenantAdminEmployeeMirrorService;
import com.worknest.tenant.service.TenantSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.regex.Pattern;

@Component
public class TenantProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningListener.class);
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final PlatformTenantRepository platformTenantRepository;
    private final TenantSchemaService tenantSchemaService;
    private final TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService;
    private final JdbcTemplate masterJdbcTemplate;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public TenantProvisioningListener(
            PlatformTenantRepository platformTenantRepository,
            TenantSchemaService tenantSchemaService,
            TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService,
            @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.platformTenantRepository = platformTenantRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.tenantAdminEmployeeMirrorService = tenantAdminEmployeeMirrorService;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Async("tenantProvisioningExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProvisioningRequest(TenantProvisioningRequestedEvent event) {
        masterTenantContextRunner.runInMasterContext(() -> {
            PlatformTenant tenant = platformTenantRepository.findById(event.tenantId()).orElse(null);
            if (tenant == null) {
                log.warn("Skipping tenant provisioning because tenant id {} was not found", event.tenantId());
                return;
            }

            if (tenant.getStatus() == TenantStatus.ACTIVE) {
                log.info("Tenant {} is already active; skipping provisioning", tenant.getTenantKey());
                return;
            }

            try {
                validateDatabaseName(tenant.getDatabaseName());
                createTenantDatabaseIfMissing(tenant.getDatabaseName());
                tenantSchemaService.ensureTenantSchema(tenant);
                tenantAdminEmployeeMirrorService.ensureTenantAdminEmployeeMirror(tenant.getTenantKey());

                tenant.setStatus(TenantStatus.ACTIVE);
                platformTenantRepository.save(tenant);
                log.info("Tenant provisioning completed successfully for {}", tenant.getTenantKey());
            } catch (Exception ex) {
                tenant.setStatus(TenantStatus.SUSPENDED);
                platformTenantRepository.save(tenant);
                log.error("Tenant provisioning failed for {}. Tenant has been suspended.",
                        tenant.getTenantKey(), ex);
            }
        });
    }

    private void createTenantDatabaseIfMissing(String databaseName) {
        masterJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "`");
    }

    private void validateDatabaseName(String databaseName) {
        if (databaseName == null || !DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Generated tenant database name is invalid");
        }
    }
}
