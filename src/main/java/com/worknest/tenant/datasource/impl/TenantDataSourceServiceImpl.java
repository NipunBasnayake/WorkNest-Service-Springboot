package com.worknest.tenant.datasource.impl;

import com.worknest.common.exception.TenantNotFoundException;
import com.worknest.common.util.AppConstants;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.tenant.datasource.TenantDataSourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantDataSourceServiceImpl implements TenantDataSourceService {

    private final DataSource masterDataSource;
    private final MasterTenantLookupService masterTenantLookupService;
    private final String defaultTenant;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceServiceImpl(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            MasterTenantLookupService masterTenantLookupService,
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant) {
        this.masterDataSource = masterDataSource;
        this.masterTenantLookupService = masterTenantLookupService;
        this.defaultTenant = defaultTenant;
    }

    @Override
    public DataSource getDataSource(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank() || defaultTenant.equalsIgnoreCase(tenantKey)) {
            return masterDataSource;
        }

        // Return cached DataSource if exists
        if (tenantDataSources.containsKey(tenantKey)) {
            return tenantDataSources.get(tenantKey);
        }

        // Load tenant from database using JdbcTemplate (no JPA dependency)
        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(tenantKey)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantKey));

        // Create and cache new DataSource
        DataSource dataSource = createDataSource(tenant);
        tenantDataSources.put(tenantKey, dataSource);

        return dataSource;
    }

    @Override
    public Map<String, DataSource> getAllDataSources() {
        return new HashMap<>(tenantDataSources);
    }

    @Override
    public void removeDataSource(String tenantKey) {
        DataSource dataSource = tenantDataSources.remove(tenantKey);
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    @Override
    public DataSource createDataSource(PlatformTenant tenant) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenant.getDbUrl());
        config.setUsername(tenant.getDbUsername());
        config.setPassword(tenant.getDbPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("TenantPool-" + tenant.getTenantKey());

        return new HikariDataSource(config);
    }
}

