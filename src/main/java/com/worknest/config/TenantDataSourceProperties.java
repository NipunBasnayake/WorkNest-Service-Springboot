package com.worknest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.tenant.datasource")
public class TenantDataSourceProperties {

    private Cache cache = new Cache();
    private Pool pool = new Pool();

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache == null ? new Cache() : cache;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool == null ? new Pool() : pool;
    }

    public static class Cache {
        private int maxCachedPools = 100;
        private long idleEvictionMs = 900_000;

        public int getMaxCachedPools() {
            return maxCachedPools;
        }

        public void setMaxCachedPools(int maxCachedPools) {
            this.maxCachedPools = maxCachedPools;
        }

        public long getIdleEvictionMs() {
            return idleEvictionMs;
        }

        public void setIdleEvictionMs(long idleEvictionMs) {
            this.idleEvictionMs = idleEvictionMs;
        }
    }

    public static class Pool {
        private int maximumPoolSize = 8;
        private int minimumIdle;
        private long connectionTimeoutMs = 30_000;
        private long idleTimeoutMs = 300_000;
        private long maxLifetimeMs = 1_800_000;
        private long validationTimeoutMs = 5_000;
        private long leakDetectionThresholdMs;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }

        public long getValidationTimeoutMs() {
            return validationTimeoutMs;
        }

        public void setValidationTimeoutMs(long validationTimeoutMs) {
            this.validationTimeoutMs = validationTimeoutMs;
        }

        public long getLeakDetectionThresholdMs() {
            return leakDetectionThresholdMs;
        }

        public void setLeakDetectionThresholdMs(long leakDetectionThresholdMs) {
            this.leakDetectionThresholdMs = leakDetectionThresholdMs;
        }
    }
}
