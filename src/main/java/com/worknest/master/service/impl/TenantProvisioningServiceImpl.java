package com.worknest.master.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.DownstreamCommunicationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.service.TenantProvisioningService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.service.TenantSchemaService;
import com.worknest.tenant.service.impl.TenantAdminEmployeeMirrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.regex.Pattern;

@Service
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningServiceImpl.class);
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final PlatformTenantRepository platformTenantRepository;
    private final TenantSchemaService tenantSchemaService;
    private final TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService;
    private final JdbcTemplate masterJdbcTemplate;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final TransactionTemplate masterTransaction;

    public TenantProvisioningServiceImpl(
            PlatformTenantRepository platformTenantRepository,
            TenantSchemaService tenantSchemaService,
            TenantAdminEmployeeMirrorService tenantAdminEmployeeMirrorService,
            @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate,
            MasterTenantContextRunner masterTenantContextRunner,
            @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager) {
        this.platformTenantRepository = platformTenantRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.tenantAdminEmployeeMirrorService = tenantAdminEmployeeMirrorService;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.masterTransaction = new TransactionTemplate(masterTransactionManager);
    }

    @Override
    public void provisionTenant(Long tenantId) {
        PlatformTenant tenant = loadTenant(tenantId);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return;
        }

        try {
            validateDatabaseName(tenant.getDatabaseName());
            masterJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + tenant.getDatabaseName() + "`");
            tenantSchemaService.ensureTenantSchema(tenant);
            tenantAdminEmployeeMirrorService.ensureTenantAdminEmployeeMirror(tenant.getTenantKey());
            updateStatus(tenantId, TenantStatus.ACTIVE);
            log.info("Tenant provisioning completed successfully for {}", tenant.getTenantKey());
        } catch (Exception exception) {
            updateStatus(tenantId, TenantStatus.INACTIVE);
            throw new DownstreamCommunicationException(
                    "Tenant provisioning could not be completed. Registration was not finalized.", exception);
        }
    }

    private PlatformTenant loadTenant(Long tenantId) {
        PlatformTenant tenant = masterTenantContextRunner.runInMasterContext(() -> masterTransaction.execute(status ->
                platformTenantRepository.findById(tenantId).orElse(null)));
        if (tenant == null) {
            throw new ResourceNotFoundException("Tenant not found with id: " + tenantId);
        }
        return tenant;
    }

    private void updateStatus(Long tenantId, TenantStatus status) {
        masterTenantContextRunner.runInMasterContext(() -> masterTransaction.executeWithoutResult(transactionStatus -> {
            PlatformTenant tenant = platformTenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
            tenant.changeStatus(status);
            platformTenantRepository.save(tenant);
        }));
    }

    private void validateDatabaseName(String databaseName) {
        if (databaseName == null || !DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Generated tenant database name is invalid");
        }
    }
}
