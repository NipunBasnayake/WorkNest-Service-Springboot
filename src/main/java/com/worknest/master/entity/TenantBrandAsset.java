package com.worknest.master.entity;

import com.worknest.master.enums.BrandAssetPurpose;
import com.worknest.master.enums.BrandThemeVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_brand_assets",
        indexes = {
                @Index(
                        name = "idx_brand_asset_active_slot",
                        columnList = "tenant_branding_id,purpose,theme_variant,active"
                ),
                @Index(name = "idx_brand_asset_stored_asset", columnList = "stored_asset_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantBrandAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_branding_id", nullable = false)
    private TenantBranding tenantBranding;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private BrandAssetPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_variant", nullable = false, length = 20)
    private BrandThemeVariant themeVariant = BrandThemeVariant.DEFAULT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_asset_id", nullable = false)
    private MasterStoredAsset storedAsset;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
