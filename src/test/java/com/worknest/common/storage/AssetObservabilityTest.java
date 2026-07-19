package com.worknest.common.storage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetObservabilityTest {

    @Test
    void recordsProcessingCacheFallbackAndInventoryMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AssetObservability observability = new AssetObservability(registry);

        observability.recordImageProcessing(ImageAssetProcessor.Profile.AVATAR, 1_000_000, false);
        observability.recordBrandingCacheHit("public");
        observability.recordFallback("branding_object_missing");
        observability.replaceInventory(List.of(
                new AssetObservability.InventorySnapshot("branding", "acme", 2, 4096, 1, 3)));

        assertThat(registry.get("worknest.image.processing.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("worknest.branding.cache.hits").counter().count()).isEqualTo(1);
        assertThat(registry.get("worknest.image.fallback.events").counter().count()).isEqualTo(1);
        assertThat(registry.get("worknest.assets.count").tag("tenant", "acme").gauge().value()).isEqualTo(2);
        assertThat(registry.get("worknest.assets.bytes").tag("tenant", "acme").gauge().value()).isEqualTo(4096);
    }
}
