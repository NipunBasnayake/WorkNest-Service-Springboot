package com.worknest.master.entity;

import com.worknest.master.enums.SubscriptionAuditAction;
import com.worknest.master.enums.SubscriptionChangeSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tenant_key", length = 50)
    private String tenantKey;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "previous_plan_code", length = 50)
    private String previousPlanCode;

    @Column(name = "new_plan_code", length = 50)
    private String newPlanCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private SubscriptionAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SubscriptionChangeSource source;

    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        occurredAt = LocalDateTime.now();
    }
}
