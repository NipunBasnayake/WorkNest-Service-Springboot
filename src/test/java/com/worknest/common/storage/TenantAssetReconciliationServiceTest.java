package com.worknest.common.storage;

import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.entity.StoredFileVariant;
import com.worknest.tenant.repository.StoredFileMetadataRepository;
import com.worknest.tenant.repository.StoredFileVariantRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAssetReconciliationServiceTest {

    @Test
    void quarantinesActiveAvatarWhenARequiredVariantIsMissing() {
        StoredFileMetadataRepository metadataRepository = mock(StoredFileMetadataRepository.class);
        StoredFileVariantRepository variantRepository = mock(StoredFileVariantRepository.class);
        StorageProvider storageProvider = mock(StorageProvider.class);
        TenantAssetReconciliationService service = new TenantAssetReconciliationService(
                metadataRepository, variantRepository, storageProvider);

        StoredFileMetadata metadata = new StoredFileMetadata();
        metadata.setId(10L);
        metadata.setStorageCategory(StorageCategory.EMPLOYEE_AVATAR);
        metadata.setRelativePath("employees/photos/1/a/original.jpg");
        metadata.setSha256("a".repeat(64));
        metadata.setFileSize(100L);
        metadata.setLifecycleState("ACTIVE");
        metadata.setActive(true);
        metadata.setUploadedAt(LocalDateTime.now());
        StoredFileVariant variant = new StoredFileVariant();
        variant.setRelativePath("employees/photos/1/a/128.jpg");
        variant.setSha256("b".repeat(64));
        variant.setFileSize(40L);

        when(metadataRepository.findAll()).thenReturn(List.of(metadata));
        when(variantRepository.findBySourceFileIdOrderByWidthAsc(10L)).thenReturn(List.of(variant));
        when(storageProvider.exists("acme", metadata.getRelativePath())).thenReturn(true);
        when(storageProvider.hashMatches("acme", metadata.getRelativePath(), metadata.getSha256())).thenReturn(true);
        when(storageProvider.exists("acme", variant.getRelativePath())).thenReturn(false);
        when(storageProvider.listObjects("acme", "employees/photos")).thenReturn(List.of());

        AssetObservability.InventorySnapshot snapshot = service.reconcile(
                "acme", Duration.ofDays(7), Duration.ofHours(24));

        assertThat(snapshot.assetCount()).isEqualTo(1);
        assertThat(snapshot.assetBytes()).isEqualTo(140);
        assertThat(snapshot.missingObjects()).isEqualTo(1);
        assertThat(metadata.isActive()).isFalse();
        assertThat(metadata.getLifecycleState()).isEqualTo("QUARANTINED");
        verify(metadataRepository).save(metadata);
    }
}
