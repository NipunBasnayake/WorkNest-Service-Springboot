package com.worknest.common.storage;

import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.entity.StoredFileVariant;
import com.worknest.tenant.repository.StoredFileMetadataRepository;
import com.worknest.tenant.repository.StoredFileVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TenantAssetReconciliationService {

    private static final String AVATAR_PREFIX = "employees/photos";

    private final StoredFileMetadataRepository metadataRepository;
    private final StoredFileVariantRepository variantRepository;
    private final StorageProvider storageProvider;

    public TenantAssetReconciliationService(
            StoredFileMetadataRepository metadataRepository,
            StoredFileVariantRepository variantRepository,
            StorageProvider storageProvider) {
        this.metadataRepository = metadataRepository;
        this.variantRepository = variantRepository;
        this.storageProvider = storageProvider;
    }

    @Transactional(transactionManager = "transactionManager")
    public AssetObservability.InventorySnapshot reconcile(
            String tenantSlug,
            Duration supersededRetention,
            Duration orphanGrace) {
        LocalDateTime supersededCutoff = LocalDateTime.now().minus(supersededRetention);
        Instant orphanCutoff = Instant.now().minus(orphanGrace);
        Set<String> referencedPaths = new HashSet<>();
        long assetCount = 0;
        long assetBytes = 0;
        long missingObjects = 0;

        for (StoredFileMetadata metadata : metadataRepository.findAll()) {
            if (metadata.getStorageCategory() != StorageCategory.EMPLOYEE_AVATAR) continue;
            List<StoredFileVariant> variants = variantRepository.findBySourceFileIdOrderByWidthAsc(metadata.getId());
            List<String> paths = assetPaths(metadata, variants);

            LocalDateTime lifecycleUpdatedAt = metadata.getUpdatedAt() == null
                    ? metadata.getUploadedAt()
                    : metadata.getUpdatedAt();
            if ("SUPERSEDED".equals(metadata.getLifecycleState())
                    && lifecycleUpdatedAt != null
                    && lifecycleUpdatedAt.isBefore(supersededCutoff)) {
                if (deleteAll(tenantSlug, paths)) {
                    metadata.setLifecycleState("DELETED");
                    metadata.setActive(false);
                    metadataRepository.save(metadata);
                }
                continue;
            }
            if ("DELETED".equals(metadata.getLifecycleState())) continue;

            referencedPaths.addAll(paths);
            if (metadata.isActive() && "ACTIVE".equals(metadata.getLifecycleState())) {
                assetCount++;
                assetBytes += totalBytes(metadata, variants);
                long missing = integrityFailureCount(tenantSlug, metadata, variants);
                missingObjects += missing;
                if (missing > 0) {
                    metadata.setLifecycleState("QUARANTINED");
                    metadata.setActive(false);
                    metadataRepository.save(metadata);
                }
            }
        }

        long orphanObjects = 0;
        for (StoredObjectDescriptor object : storageProvider.listObjects(tenantSlug, AVATAR_PREFIX)) {
            if (!referencedPaths.contains(object.relativePath())
                    && object.lastModified().isBefore(orphanCutoff)) {
                orphanObjects++;
                storageProvider.delete(tenantSlug, object.relativePath());
            }
        }
        return new AssetObservability.InventorySnapshot(
                "avatar", tenantSlug, assetCount, assetBytes, missingObjects, orphanObjects);
    }

    private List<String> assetPaths(StoredFileMetadata metadata, List<StoredFileVariant> variants) {
        List<String> paths = new ArrayList<>();
        paths.add(metadata.getRelativePath());
        paths.addAll(variants.stream().map(StoredFileVariant::getRelativePath).toList());
        return paths;
    }

    private long totalBytes(StoredFileMetadata metadata, List<StoredFileVariant> variants) {
        long total = metadata.getFileSize() == null ? 0 : metadata.getFileSize();
        return total + variants.stream()
                .map(StoredFileVariant::getFileSize)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private long integrityFailureCount(
            String tenantSlug,
            StoredFileMetadata metadata,
            List<StoredFileVariant> variants) {
        long failures = storageProvider.exists(tenantSlug, metadata.getRelativePath())
                && storageProvider.hashMatches(tenantSlug, metadata.getRelativePath(), metadata.getSha256()) ? 0 : 1;
        return failures + variants.stream()
                .filter(variant -> !storageProvider.exists(tenantSlug, variant.getRelativePath())
                        || !storageProvider.hashMatches(tenantSlug, variant.getRelativePath(), variant.getSha256()))
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
}
