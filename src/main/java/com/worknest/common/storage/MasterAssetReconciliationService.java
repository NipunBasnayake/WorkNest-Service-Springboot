package com.worknest.common.storage;

import com.worknest.master.entity.MasterStoredAsset;
import com.worknest.master.entity.MasterStoredAssetVariant;
import com.worknest.master.enums.AssetLifecycleState;
import com.worknest.master.repository.MasterStoredAssetRepository;
import com.worknest.master.repository.PlatformTenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MasterAssetReconciliationService {

    private static final String BRANDING_PREFIX = "companies/logos";

    private final MasterStoredAssetRepository assetRepository;
    private final PlatformTenantRepository tenantRepository;
    private final StorageProvider storageProvider;

    public MasterAssetReconciliationService(
            MasterStoredAssetRepository assetRepository,
            PlatformTenantRepository tenantRepository,
            StorageProvider storageProvider) {
        this.assetRepository = assetRepository;
        this.tenantRepository = tenantRepository;
        this.storageProvider = storageProvider;
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public List<AssetObservability.InventorySnapshot> reconcile(
            Duration supersededRetention,
            Duration orphanGrace) {
        LocalDateTime supersededCutoff = LocalDateTime.now().minus(supersededRetention);
        Instant orphanCutoff = Instant.now().minus(orphanGrace);
        Map<String, TenantInventory> inventories = new HashMap<>();
        tenantRepository.findAll().forEach(tenant ->
                inventories.putIfAbsent(tenant.getSlug(), new TenantInventory()));

        for (MasterStoredAsset asset : assetRepository.findAll()) {
            String tenantSlug = asset.getTenant().getSlug();
            TenantInventory inventory = inventories.computeIfAbsent(tenantSlug, ignored -> new TenantInventory());
            List<String> paths = assetPaths(asset);

            if (asset.getLifecycleState() == AssetLifecycleState.SUPERSEDED
                    && asset.getUpdatedAt() != null
                    && asset.getUpdatedAt().isBefore(supersededCutoff)) {
                if (deleteAll(tenantSlug, paths)) {
                    asset.setLifecycleState(AssetLifecycleState.DELETED);
                    assetRepository.save(asset);
                }
                continue;
            }
            if (asset.getLifecycleState() == AssetLifecycleState.DELETED) continue;

            inventory.referencedPaths.addAll(paths);
            if (asset.getLifecycleState() == AssetLifecycleState.ACTIVE) {
                inventory.assetCount++;
                inventory.assetBytes += totalBytes(asset);
                long missing = integrityFailureCount(tenantSlug, asset);
                inventory.missingObjects += missing;
                if (missing > 0) {
                    asset.setLifecycleState(AssetLifecycleState.QUARANTINED);
                    assetRepository.save(asset);
                }
            }
        }

        for (Map.Entry<String, TenantInventory> entry : inventories.entrySet()) {
            String tenantSlug = entry.getKey();
            TenantInventory inventory = entry.getValue();
            for (StoredObjectDescriptor object : storageProvider.listObjects(tenantSlug, BRANDING_PREFIX)) {
                if (!inventory.referencedPaths.contains(object.relativePath())
                        && object.lastModified().isBefore(orphanCutoff)) {
                    inventory.orphanObjects++;
                    storageProvider.delete(tenantSlug, object.relativePath());
                }
            }
        }

        return inventories.entrySet().stream()
                .map(entry -> entry.getValue().snapshot("branding", entry.getKey()))
                .toList();
    }

    private List<String> assetPaths(MasterStoredAsset asset) {
        List<String> paths = new ArrayList<>();
        paths.add(asset.getObjectKey());
        paths.addAll(asset.getVariants().stream().map(MasterStoredAssetVariant::getObjectKey).toList());
        return paths;
    }

    private long totalBytes(MasterStoredAsset asset) {
        long total = asset.getFileSize() == null ? 0 : asset.getFileSize();
        return total + asset.getVariants().stream()
                .map(MasterStoredAssetVariant::getFileSize)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private long integrityFailureCount(String tenantSlug, MasterStoredAsset asset) {
        long failures = storageProvider.exists(tenantSlug, asset.getObjectKey())
                && storageProvider.hashMatches(tenantSlug, asset.getObjectKey(), asset.getSha256()) ? 0 : 1;
        return failures + asset.getVariants().stream()
                .filter(variant -> !storageProvider.exists(tenantSlug, variant.getObjectKey())
                        || !storageProvider.hashMatches(tenantSlug, variant.getObjectKey(), variant.getSha256()))
                .count();
    }

    private boolean deleteAll(String tenantSlug, List<String> paths) {
        boolean succeeded = true;
        for (String path : paths) {
            try {
                storageProvider.delete(tenantSlug, path);
            } catch (RuntimeException exception) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    private static final class TenantInventory {
        private final Set<String> referencedPaths = new HashSet<>();
        private long assetCount;
        private long assetBytes;
        private long missingObjects;
        private long orphanObjects;

        private AssetObservability.InventorySnapshot snapshot(String scope, String tenant) {
            return new AssetObservability.InventorySnapshot(
                    scope, tenant, assetCount, assetBytes, missingObjects, orphanObjects);
        }
    }
}
