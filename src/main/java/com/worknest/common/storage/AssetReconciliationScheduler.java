package com.worknest.common.storage;

import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.multitenancy.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "storage.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class AssetReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AssetReconciliationScheduler.class);

    private final PlatformTenantRepository tenantRepository;
    private final TenantAssetReconciliationService tenantReconciliation;
    private final AssetObservability observability;
    private final Duration supersededRetention;
    private final Duration orphanGrace;

    public AssetReconciliationScheduler(
            PlatformTenantRepository tenantRepository,
            TenantAssetReconciliationService tenantReconciliation,
            AssetObservability observability,
            @Value("${storage.reconciliation.superseded-retention:P7D}") Duration supersededRetention,
            @Value("${storage.reconciliation.orphan-grace:PT24H}") Duration orphanGrace) {
        this.tenantRepository = tenantRepository;
        this.tenantReconciliation = tenantReconciliation;
        this.observability = observability;
        this.supersededRetention = supersededRetention;
        this.orphanGrace = orphanGrace;
    }

    @Scheduled(
            cron = "${storage.reconciliation.cron:0 17 3 * * *}",
            zone = "${storage.reconciliation.time-zone:UTC}")
    public void reconcile() {
        List<AssetObservability.InventorySnapshot> snapshots = new ArrayList<>();
        for (PlatformTenant tenant : tenantRepository.findAll()) {
            if (!Boolean.TRUE.equals(tenant.getActive())) continue;
            try {
                snapshots.add(inTenantContext(tenant, () -> tenantReconciliation.reconcile(
                        tenant.getSlug(), supersededRetention, orphanGrace)));
            } catch (RuntimeException exception) {
                observability.recordReconciliationFailure("avatar");
                log.error("Avatar asset reconciliation failed for tenant {}", tenant.getTenantKey(), exception);
            }
        }
        observability.replaceInventory(snapshots);
    }

    private <T> T inTenantContext(PlatformTenant tenant, java.util.function.Supplier<T> operation) {
        String previousKey = TenantContextHolder.getTenantKey();
        String previousSlug = TenantContextHolder.getTenantSlug();
        try {
            TenantContextHolder.setTenantKey(tenant.getTenantKey());
            TenantContextHolder.setTenantSlug(tenant.getSlug());
            return operation.get();
        } finally {
            TenantContextHolder.clear();
            if (previousKey != null) TenantContextHolder.setTenantKey(previousKey);
            if (previousSlug != null) TenantContextHolder.setTenantSlug(previousSlug);
        }
    }
}
