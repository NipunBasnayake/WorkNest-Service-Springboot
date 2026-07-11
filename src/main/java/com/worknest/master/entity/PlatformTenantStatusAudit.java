package com.worknest.master.entity;

import com.worknest.common.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_tenant_status_audit")
@Getter
@NoArgsConstructor
public class PlatformTenantStatusAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "tenant_key", nullable = false, length = 50)
    private String tenantKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private TenantStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TenantStatus newStatus;

    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public PlatformTenantStatusAudit(Long tenantId, String tenantKey, TenantStatus previousStatus, TenantStatus newStatus, String actorEmail) {
        this.tenantId = tenantId;
        this.tenantKey = tenantKey;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actorEmail = actorEmail;
    }

    @PrePersist
    void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
