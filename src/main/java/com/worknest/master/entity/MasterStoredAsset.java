package com.worknest.master.entity;

import com.worknest.master.enums.AssetLifecycleState;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "master_stored_assets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_stored_asset_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_master_stored_asset_object_key", columnNames = "object_key")
        },
        indexes = {
                @Index(name = "idx_master_asset_tenant_lifecycle", columnList = "tenant_id,lifecycle_state"),
                @Index(name = "idx_master_asset_sha256", columnList = "sha256")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MasterStoredAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private PlatformTenant tenant;

    @Column(name = "provider_name", nullable = false, length = 40)
    private String providerName = "LOCAL";

    @Column(name = "object_key", nullable = false, length = 700)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "extension", nullable = false, length = 10)
    private String extension;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "pixel_width", nullable = false)
    private Integer width;

    @Column(name = "pixel_height", nullable = false)
    private Integer height;

    @Column(name = "public_asset", nullable = false)
    private boolean publicAsset = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private AssetLifecycleState lifecycleState = AssetLifecycleState.ACTIVE;

    @Column(name = "transformation_version", nullable = false)
    private Integer transformationVersion = 1;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MasterStoredAssetVariant> variants = new ArrayList<>();

    public void addVariant(MasterStoredAssetVariant variant) {
        variant.setAsset(this);
        variants.add(variant);
    }

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
