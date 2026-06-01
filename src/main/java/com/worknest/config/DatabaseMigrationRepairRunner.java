package com.worknest.config;

import com.worknest.tenant.context.MasterTenantContextRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(15)
public class DatabaseMigrationRepairRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRepairRunner.class);

    private final JdbcTemplate masterJdbcTemplate;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public DatabaseMigrationRepairRunner(JdbcTemplate masterJdbcTemplate, MasterTenantContextRunner masterTenantContextRunner) {
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Override
    public void run(String... args) {
        masterTenantContextRunner.runInMasterContext(() -> {
            try {
                log.info("[TENANT] Running tenant database state repairs...");

                // If slug is missing or empty, set it to the tenant_key
                masterJdbcTemplate.execute(
                    "UPDATE platform_tenants " +
                    "SET slug = tenant_key " +
                    "WHERE slug IS NULL OR slug = ''"
                );

                // Set active = 1 for ACTIVE tenants
                masterJdbcTemplate.execute(
                    "UPDATE platform_tenants " +
                    "SET active = 1 " +
                    "WHERE status = 'ACTIVE' AND active = 0"
                );

                log.info("[TENANT] Tenant database state repairs completed successfully.");
            } catch (Exception e) {
                log.error("[TENANT] Failed to run database repairs: {}", e.getMessage(), e);
            }
        });
    }
}
