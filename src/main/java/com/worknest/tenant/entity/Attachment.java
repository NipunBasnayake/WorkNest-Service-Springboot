package com.worknest.tenant.entity;

import com.worknest.tenant.enums.AttachmentEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_attachments_uploaded_by", columnList = "uploaded_by_id"),
        @Index(name = "idx_attachments_uploaded_by_user", columnList = "uploaded_by_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AttachmentEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "file_type", length = 120)
    private String fileType;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "uploaded_by_id", nullable = true)
    private Employee uploadedBy;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
