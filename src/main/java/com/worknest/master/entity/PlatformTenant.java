package com.worknest.master.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, unique = true, length = 50)
    private String tenantKey;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    @Column(name = "db_url", nullable = false, length = 500)
    private String dbUrl;

    @Column(name = "db_username", nullable = false, length = 100)
    private String dbUsername;

    @Column(name = "db_password", nullable = false, length = 255)
    private String dbPassword;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

