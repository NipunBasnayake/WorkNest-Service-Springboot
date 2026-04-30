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
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    private final int maxCachedPools;
    private final long cacheIdleEvictionMs;

    private final int poolMaximumSize;
    private final int poolMinimumIdle;
    private final long poolConnectionTimeoutMs;
    private final long poolIdleTimeoutMs;
    private final long poolMaxLifetimeMs;
    private final long poolValidationTimeoutMs;
    private final long poolLeakDetectionThresholdMs;

    private final ConcurrentMap<String, TenantPoolHolder> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceServiceImpl(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            MasterTenantLookupService masterTenantLookupService,
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant,
            @Value("${app.tenant.datasource.cache.max-cached-pools:100}") int maxCachedPools,
            @Value("${app.tenant.datasource.cache.idle-eviction-ms:900000}") long cacheIdleEvictionMs,
            @Value("${app.tenant.datasource.pool.maximum-pool-size:8}") int poolMaximumSize,
            @Value("${app.tenant.datasource.pool.minimum-idle:0}") int poolMinimumIdle,
            @Value("${app.tenant.datasource.pool.connection-timeout-ms:30000}") long poolConnectionTimeoutMs,
            @Value("${app.tenant.datasource.pool.idle-timeout-ms:300000}") long poolIdleTimeoutMs,
            @Value("${app.tenant.datasource.pool.max-lifetime-ms:1800000}") long poolMaxLifetimeMs,
            @Value("${app.tenant.datasource.pool.validation-timeout-ms:5000}") long poolValidationTimeoutMs,
            @Value("${app.tenant.datasource.pool.leak-detection-threshold-ms:0}") long poolLeakDetectionThresholdMs) {
        this.masterDataSource = masterDataSource;
        this.masterTenantLookupService = masterTenantLookupService;
        this.defaultTenant = defaultTenant;
        this.maxCachedPools = maxCachedPools;
        this.cacheIdleEvictionMs = cacheIdleEvictionMs;
        this.poolMaximumSize = poolMaximumSize;
        this.poolMinimumIdle = poolMinimumIdle;
        this.poolConnectionTimeoutMs = poolConnectionTimeoutMs;
        this.poolIdleTimeoutMs = poolIdleTimeoutMs;
        this.poolMaxLifetimeMs = poolMaxLifetimeMs;
        this.poolValidationTimeoutMs = poolValidationTimeoutMs;
        this.poolLeakDetectionThresholdMs = poolLeakDetectionThresholdMs;
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

        holder.touch(now);
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
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(tenant.getDbUrl());
        config.setUsername(tenant.getDbUsername());
        config.setPassword(tenant.getDbPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        config.setMaximumPoolSize(poolMaximumSize);
        config.setMinimumIdle(poolMinimumIdle);
        config.setConnectionTimeout(poolConnectionTimeoutMs);
        config.setIdleTimeout(poolIdleTimeoutMs);
        config.setMaxLifetime(poolMaxLifetimeMs);
        config.setValidationTimeout(poolValidationTimeoutMs);
        if (poolLeakDetectionThresholdMs > 0) {
            config.setLeakDetectionThreshold(poolLeakDetectionThresholdMs);
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
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantResolutionException("Tenant is not active: " + tenantKey);
        }
        return tenant;
    }

    private boolean isIdleForEviction(TenantPoolHolder holder, long nowEpochMs) {
        if (nowEpochMs - holder.lastAccessEpochMs() < cacheIdleEvictionMs) {
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

