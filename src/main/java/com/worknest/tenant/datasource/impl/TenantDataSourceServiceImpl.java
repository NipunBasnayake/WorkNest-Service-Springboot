package com.worknest.tenant.datasource.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.TenantNotFoundException;
import com.worknest.common.exception.TenantResolutionException;
import com.worknest.common.util.AppConstants;
import com.worknest.config.DatabaseDataSourceSupport;
import com.worknest.config.TenantDataSourceProperties;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.tenant.datasource.TenantDataSourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TenantDataSourceServiceImpl implements TenantDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceServiceImpl.class);

    private final DataSource masterDataSource;
    private final MasterTenantLookupService masterTenantLookupService;
    private final String defaultTenant;
    private final TenantDataSourceProperties dataSourceProperties;
    private final String driverClassName;

    private final ConcurrentMap<String, TenantPoolHolder> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceServiceImpl(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            MasterTenantLookupService masterTenantLookupService,
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant,
            @Qualifier("masterDataSourceProperties") DataSourceProperties masterDataSourceProperties,
            TenantDataSourceProperties dataSourceProperties) {
        this.masterDataSource = masterDataSource;
        this.masterTenantLookupService = masterTenantLookupService;
        this.defaultTenant = defaultTenant;
        this.dataSourceProperties = dataSourceProperties;
        this.driverClassName = masterDataSourceProperties.getDriverClassName();
        DatabaseDataSourceSupport.requireMySqlUrl(masterDataSourceProperties.getUrl());
    }

    @Override
    public DataSource getDataSource(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null || defaultTenant.equalsIgnoreCase(normalizedTenantKey)) {
            return masterDataSource;
        }

        long now = System.currentTimeMillis();
        TenantPoolHolder holder = tenantDataSources.compute(normalizedTenantKey, (key, existing) -> {
            if (existing != null && !existing.dataSource().isClosed()) {
                existing.touch(now);
                return existing;
            }

            PlatformTenant tenant = getActiveTenantOrThrow(key);
            HikariDataSource dataSource = (HikariDataSource) createDataSource(tenant);
            log.info("Created tenant datasource pool for {}", key);
            return new TenantPoolHolder(dataSource, now);
        });

        /* touch() is already called inside compute() when the pool existed.
         * For a new pool, the creation timestamp serves as the initial access time.
         * Calling it again here would be redundant. */
        evictPoolsIfAboveLimit();
        return holder.dataSource();
    }

    @Override
    public Map<String, DataSource> getAllDataSources() {
        Map<String, DataSource> snapshot = new HashMap<>();
        tenantDataSources.forEach((tenantKey, holder) -> snapshot.put(tenantKey, holder.dataSource()));
        return snapshot;
    }

    @Override
    public void removeDataSource(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return;
        }

        TenantPoolHolder removed = tenantDataSources.remove(normalizedTenantKey);
        closeQuietly(removed, normalizedTenantKey, "manual-remove");
    }

    @Override
    public DataSource createDataSource(PlatformTenant tenant) {
        DatabaseDataSourceSupport.requireMySqlUrl(tenant.getDbUrl());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenant.getDbUrl());
        config.setUsername(tenant.getDbUsername());
        config.setPassword(tenant.getDbPassword());
        String configuredDriver = DatabaseDataSourceSupport.trimToNull(driverClassName);
        if (configuredDriver != null) {
            config.setDriverClassName(configuredDriver);
        }

        TenantDataSourceProperties.Pool pool = dataSourceProperties.getPool();
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeoutMs());
        config.setIdleTimeout(pool.getIdleTimeoutMs());
        config.setMaxLifetime(pool.getMaxLifetimeMs());
        config.setValidationTimeout(pool.getValidationTimeoutMs());
        if (pool.getLeakDetectionThresholdMs() > 0) {
            config.setLeakDetectionThreshold(pool.getLeakDetectionThresholdMs());
        }
        config.setPoolName("WorkNestTenantPool-" + tenant.getTenantKey());
        config.setRegisterMbeans(false);

        return new HikariDataSource(config);
    }

    @Scheduled(fixedDelayString = "${app.tenant.datasource.cache.eviction-interval-ms:60000}")
    public void evictIdlePools() {
        long now = System.currentTimeMillis();
        tenantDataSources.forEach((tenantKey, holder) -> {
            HikariDataSource dataSource = holder.dataSource();
            if (dataSource.isClosed()) {
                tenantDataSources.remove(tenantKey, holder);
                return;
            }
            if (!isIdleForEviction(holder, now)) {
                return;
            }
            if (tenantDataSources.remove(tenantKey, holder)) {
                closeQuietly(holder, tenantKey, "idle-eviction");
            }
        });
    }

    @PreDestroy
    public void shutdownPools() {
        tenantDataSources.forEach((tenantKey, holder) -> closeQuietly(holder, tenantKey, "shutdown"));
        tenantDataSources.clear();
    }

    private void evictPoolsIfAboveLimit() {
        int currentSize = tenantDataSources.size();
        int maxCachedPools = dataSourceProperties.getCache().getMaxCachedPools();
        if (currentSize <= maxCachedPools) {
            return;
        }

        List<Map.Entry<String, TenantPoolHolder>> candidates = new ArrayList<>(tenantDataSources.entrySet());
        candidates.sort(Comparator.comparingLong(entry -> entry.getValue().lastAccessEpochMs()));

        for (Map.Entry<String, TenantPoolHolder> candidate : candidates) {
            if (tenantDataSources.size() <= maxCachedPools) {
                break;
            }
            if (!isIdleForEviction(candidate.getValue(), System.currentTimeMillis())) {
                continue;
            }
            if (tenantDataSources.remove(candidate.getKey(), candidate.getValue())) {
                closeQuietly(candidate.getValue(), candidate.getKey(), "max-cache-eviction");
            }
        }

        if (tenantDataSources.size() > maxCachedPools) {
            log.warn("Tenant datasource cache is above max limit (current={}, max={}) due to active pools",
                    tenantDataSources.size(), maxCachedPools);
        }
    }

    private PlatformTenant getActiveTenantOrThrow(String tenantKey) {
        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(tenantKey)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantKey));
        if (tenant.getStatus() != TenantStatus.ACTIVE && tenant.getStatus() != TenantStatus.PROVISIONING) {
            throw new TenantResolutionException("Tenant is not active: " + tenantKey);
        }
        return tenant;
    }

    private boolean isIdleForEviction(TenantPoolHolder holder, long nowEpochMs) {
        if (nowEpochMs - holder.lastAccessEpochMs() < dataSourceProperties.getCache().getIdleEvictionMs()) {
            return false;
        }

        HikariPoolMXBean poolMxBean = holder.dataSource().getHikariPoolMXBean();
        if (poolMxBean == null) {
            return true;
        }

        return poolMxBean.getActiveConnections() == 0;
    }

    private void closeQuietly(TenantPoolHolder holder, String tenantKey, String reason) {
        if (holder == null) {
            return;
        }
        try {
            holder.dataSource().close();
            log.info("Closed tenant datasource pool for {} (reason={})", tenantKey, reason);
        } catch (Exception ex) {
            log.warn("Failed to close tenant datasource pool for {} (reason={})", tenantKey, reason, ex);
        }
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static final class TenantPoolHolder {
        private final HikariDataSource dataSource;
        private final AtomicLong lastAccessEpochMs;

        private TenantPoolHolder(HikariDataSource dataSource, long lastAccessEpochMs) {
            this.dataSource = dataSource;
            this.lastAccessEpochMs = new AtomicLong(lastAccessEpochMs);
        }

        private HikariDataSource dataSource() {
            return dataSource;
        }

        private long lastAccessEpochMs() {
            return lastAccessEpochMs.get();
        }

        private void touch(long nowEpochMs) {
            lastAccessEpochMs.set(nowEpochMs);
        }
    }
}
