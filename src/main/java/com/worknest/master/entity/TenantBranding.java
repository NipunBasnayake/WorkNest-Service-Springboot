package com.worknest.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_branding",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenant_branding_tenant", columnNames = "tenant_id")
        },
        indexes = {
                @Index(name = "idx_tenant_branding_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenantBranding {

    public static final String DEFAULT_PRIMARY_COLOR = "#9332EA";
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private PlatformTenant tenant;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = DEFAULT_PRIMARY_COLOR;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (primaryColor == null) primaryColor = DEFAULT_PRIMARY_COLOR;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
