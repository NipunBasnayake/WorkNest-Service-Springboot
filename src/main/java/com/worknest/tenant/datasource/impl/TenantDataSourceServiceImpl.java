package com.worknest.tenant.datasource.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.TenantNotFoundException;
import com.worknest.common.exception.TenantResolutionException;
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
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null || defaultTenant.equalsIgnoreCase(normalizedTenantKey)) {
            return masterDataSource;
        }

        // Return cached DataSource if exists
        if (tenantDataSources.containsKey(normalizedTenantKey)) {
            return tenantDataSources.get(normalizedTenantKey);
        }

        // Load tenant from database using JdbcTemplate (no JPA dependency)
        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(normalizedTenantKey)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + normalizedTenantKey));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantResolutionException("Tenant is not active: " + normalizedTenantKey);
        }

        // Create and cache new DataSource
        DataSource dataSource = createDataSource(tenant);
        tenantDataSources.put(normalizedTenantKey, dataSource);

        return dataSource;
    }

    @Override
    public Map<String, DataSource> getAllDataSources() {
        return new HashMap<>(tenantDataSources);
    }

    @Override
    public void removeDataSource(String tenantKey) {
        DataSource dataSource = tenantDataSources.remove(normalizeTenantKey(tenantKey));
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

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}

