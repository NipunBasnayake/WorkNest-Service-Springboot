package com.worknest.config;

import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the in-memory BI cache bounded while still absorbing dashboard refresh bursts. */
@Component
public class AnalyticsCacheConfig {
    private final CacheManager cacheManager;

    public AnalyticsCacheConfig(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRateString = "${worknest.analytics.cache-ttl-ms:45000}")
    public void expireBusinessIntelligenceSnapshots() {
        var cache = cacheManager.getCache("businessIntelligence");
        if (cache != null) cache.clear();
        var platformCache = cacheManager.getCache("platformOperations");
        if (platformCache != null) platformCache.clear();
    }
}
