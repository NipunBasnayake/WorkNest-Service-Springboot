package com.worknest.master.repository;

import com.worknest.master.entity.TenantBrandAsset;
import com.worknest.master.enums.BrandAssetPurpose;
import com.worknest.master.enums.BrandThemeVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantBrandAssetRepository extends JpaRepository<TenantBrandAsset, Long> {
    Optional<TenantBrandAsset> findFirstByTenantBrandingIdAndPurposeAndThemeVariantAndActiveTrue(
            Long tenantBrandingId,
            BrandAssetPurpose purpose,
            BrandThemeVariant themeVariant);
}
