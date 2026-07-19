package com.worknest.tenant.entity;

import com.worknest.common.storage.StorageCategory;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
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
        name = "stored_files",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stored_files_relative_path", columnNames = "relative_path")
        },
        indexes = {
                @Index(name = "idx_stored_files_reference", columnList = "related_module,related_entity_id"),
                @Index(name = "idx_stored_files_uploader", columnList = "uploaded_by_user_id"),
                @Index(name = "idx_stored_files_lifecycle", columnList = "storage_category,lifecycle_state,active")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class StoredFileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "relative_path", nullable = false, length = 700)
    private String relativePath;

    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "pixel_width")
    private Integer width;

    @Column(name = "pixel_height")
    private Integer height;

    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private String lifecycleState = "ACTIVE";

    @Column(name = "transformation_version", nullable = false)
    private Integer transformationVersion = 1;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_category", nullable = false, length = 50)
    private StorageCategory storageCategory;

    @Column(name = "related_module", length = 50)
    private String relatedModule;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "sourceFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredFileVariant> variants = new ArrayList<>();

    public void addVariant(StoredFileVariant variant) {
        variant.setSourceFile(this);
        variants.add(variant);
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (uploadedAt == null) uploadedAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
