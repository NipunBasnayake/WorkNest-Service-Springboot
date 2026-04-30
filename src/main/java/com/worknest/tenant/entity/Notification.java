package com.worknest.tenant.entity;

import com.worknest.tenant.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id,is_read"),
                @Index(name = "idx_notifications_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Employee recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "related_entity_type", length = 80)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (!read) {
            readAt = null;
        }
    }

    public String getReferenceType() {
        return relatedEntityType;
    }

    public void setReferenceType(String referenceType) {
        this.relatedEntityType = referenceType;
    }

    public Long getReferenceId() {
        return relatedEntityId;
    }

    public void setReferenceId(Long referenceId) {
        this.relatedEntityId = referenceId;
    }
}
