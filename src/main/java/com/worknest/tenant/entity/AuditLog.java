package com.worknest.tenant.entity;

import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_action_entity", columnList = "action,entity_type"),
                @Index(name = "idx_audit_logs_actor", columnList = "actor_id"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Employee actor;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 60)
    private AuditEntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
