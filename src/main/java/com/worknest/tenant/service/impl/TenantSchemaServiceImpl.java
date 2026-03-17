package com.worknest.tenant.service.impl;

import com.worknest.master.entity.PlatformTenant;
import com.worknest.tenant.datasource.TenantDataSourceService;
import com.worknest.tenant.service.TenantSchemaService;
import org.flywaydb.core.Flyway;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.stream.Stream;

@Service
public class TenantSchemaServiceImpl implements TenantSchemaService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaServiceImpl.class);

    private final TenantDataSourceService tenantDataSourceService;
    private final String[] flywayLocations;
    private final boolean baselineOnMigrate;
    private final boolean validateOnMigrate;

    public TenantSchemaServiceImpl(
            TenantDataSourceService tenantDataSourceService,
            @Value("${app.tenant.migration.locations:classpath:db/migration/tenant}") String flywayLocationsRaw,
            @Value("${app.tenant.migration.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${app.tenant.migration.validate-on-migrate:true}") boolean validateOnMigrate) {
        this.tenantDataSourceService = tenantDataSourceService;
        this.flywayLocations = Stream.of(flywayLocationsRaw.split(","))
                .map(String::trim)
                .filter(location -> !location.isBlank())
                .toArray(String[]::new);
        this.baselineOnMigrate = baselineOnMigrate;
        this.validateOnMigrate = validateOnMigrate;
    }

    @Override
    public void ensureTenantSchema(PlatformTenant tenant) {
        DataSource tenantDataSource = tenantDataSourceService.createDataSource(tenant);
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(tenantDataSource)
                    .locations(flywayLocations)
                    .baselineOnMigrate(baselineOnMigrate)
                    .validateOnMigrate(validateOnMigrate)
                    .load();
            flyway.migrate();
            log.info("Flyway tenant migration applied for {} using locations {}",
                    tenant.getTenantKey(), Arrays.toString(flywayLocations));
        } finally {
            if (tenantDataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
    }
}
