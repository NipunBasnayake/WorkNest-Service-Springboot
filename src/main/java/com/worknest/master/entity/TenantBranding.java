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
                @UniqueConstraint(name = "uk_tenant_branding_tenant", columnNames = "tenant_id"),
                @UniqueConstraint(name = "uk_tenant_branding_custom_domain", columnNames = "custom_domain")
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
    public static final int CURRENT_TOKEN_ALGORITHM_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private PlatformTenant tenant;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = DEFAULT_PRIMARY_COLOR;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Column(name = "theme_mode_policy", nullable = false, length = 20)
    private String themeModePolicy = "SYSTEM";

    @Column(name = "white_label_mode", nullable = false)
    private boolean whiteLabelMode;

    @Column(name = "powered_by_worknest", nullable = false)
    private boolean poweredByWorkNest = true;

    @Column(name = "custom_domain", length = 255)
    private String customDomain;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "token_algorithm_version", nullable = false)
    private int tokenAlgorithmVersion = CURRENT_TOKEN_ALGORITHM_VERSION;

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
