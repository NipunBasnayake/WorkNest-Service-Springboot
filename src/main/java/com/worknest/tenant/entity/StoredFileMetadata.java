package com.worknest.tenant.entity;

import com.worknest.common.storage.StorageCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files", indexes = {
        @Index(name = "idx_stored_files_reference", columnList = "related_module,related_entity_id"),
        @Index(name = "idx_stored_files_uploader", columnList = "uploaded_by_user_id"),
        @Index(name = "uk_stored_files_relative_path", columnList = "relative_path", unique = true)
})
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

    @Column(name = "relative_path", nullable = false, unique = true, length = 700)
    private String relativePath;

    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_category", nullable = false, length = 50)
    private StorageCategory storageCategory;

    @Column(name = "related_module", length = 50)
    private String relatedModule;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}
