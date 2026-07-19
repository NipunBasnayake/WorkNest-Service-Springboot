package com.worknest.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stored_file_variants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stored_file_variant", columnNames = {"source_file_id", "variant_name"}),
        @UniqueConstraint(name = "uk_stored_file_variant_path", columnNames = "relative_path")
})
@Getter
@Setter
@NoArgsConstructor
public class StoredFileVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id", nullable = false)
    private StoredFileMetadata sourceFile;

    @Column(name = "variant_name", nullable = false, length = 30)
    private String variantName;

    @Column(name = "relative_path", nullable = false, unique = true, length = 700)
    private String relativePath;

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
}
