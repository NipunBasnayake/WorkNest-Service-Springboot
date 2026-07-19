package com.worknest.common.storage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class AssetObservability {

    public record InventorySnapshot(
            String scope,
            String tenant,
            long assetCount,
            long assetBytes,
            long missingObjects,
            long orphanObjects) {
    }

    private final MeterRegistry meterRegistry;
    private final MultiGauge assetCount;
    private final MultiGauge assetBytes;
    private final MultiGauge missingObjects;
    private final MultiGauge orphanObjects;

    public AssetObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.assetCount = MultiGauge.builder("worknest.assets.count")
                .description("Managed image asset count by scope and tenant")
                .register(meterRegistry);
        this.assetBytes = MultiGauge.builder("worknest.assets.bytes")
                .description("Managed image bytes by scope and tenant")
                .baseUnit("bytes")
                .register(meterRegistry);
        this.missingObjects = MultiGauge.builder("worknest.assets.missing.objects")
                .description("Metadata references whose object bytes are missing")
                .register(meterRegistry);
        this.orphanObjects = MultiGauge.builder("worknest.assets.orphan.objects")
                .description("Unreferenced objects found by reconciliation")
                .register(meterRegistry);
    }

    public void recordImageProcessing(ImageAssetProcessor.Profile profile, long elapsedNanos, boolean success) {
        String profileTag = profile.name().toLowerCase();
        Timer.builder("worknest.image.processing.duration")
                .description("Decoded image validation and variant generation time")
                .tag("profile", profileTag)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (!success) {
            Counter.builder("worknest.image.processing.failures")
                    .description("Image processing failures")
                    .tag("profile", profileTag)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public <T> T timeBrandingBootstrap(String scope, Supplier<T> operation) {
        return Timer.builder("worknest.branding.bootstrap.duration")
                .description("Tenant branding snapshot resolution time")
                .tag("scope", scope)
                .register(meterRegistry)
                .record(operation);
    }

    public void recordBrandingCacheHit(String scope) {
        Counter.builder("worknest.branding.cache.hits")
                .description("Branding conditional requests served as not modified")
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void recordFallback(String reason) {
        Counter.builder("worknest.image.fallback.events")
                .description("Server-detected image fallback events")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordReconciliationFailure(String scope) {
        Counter.builder("worknest.assets.reconciliation.failures")
                .description("Asset reconciliation failures")
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void replaceInventory(List<InventorySnapshot> snapshots) {
        assetCount.register(rows(snapshots, InventorySnapshot::assetCount), true);
        assetBytes.register(rows(snapshots, InventorySnapshot::assetBytes), true);
        missingObjects.register(rows(snapshots, InventorySnapshot::missingObjects), true);
        orphanObjects.register(rows(snapshots, InventorySnapshot::orphanObjects), true);
    }

    private List<MultiGauge.Row<?>> rows(
            List<InventorySnapshot> snapshots,
            java.util.function.ToLongFunction<InventorySnapshot> value) {
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        for (InventorySnapshot snapshot : snapshots) {
            rows.add(MultiGauge.Row.of(
                    Tags.of("scope", snapshot.scope(), "tenant", snapshot.tenant()),
                    Long.valueOf(value.applyAsLong(snapshot))));
        }
        return List.copyOf(rows);
    }
}
