package com.worknest.master.entity;

import com.worknest.auth.dto.LoginAttemptBucketType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "auth_login_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_login_attempts_bucket", columnNames = {"bucket_type", "bucket_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket_type", nullable = false, length = 20)
    private LoginAttemptBucketType bucketType;

    @Column(name = "bucket_key", nullable = false, length = 255)
    private String bucketKey;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_ip_address", length = 100)
    private String lastIpAddress;

    @Column(name = "last_user_agent", length = 500)
    private String lastUserAgent;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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