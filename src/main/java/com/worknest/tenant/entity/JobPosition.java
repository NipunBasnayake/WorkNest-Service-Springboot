package com.worknest.tenant.entity;

import com.worknest.tenant.enums.EmploymentType;
import com.worknest.tenant.enums.JobPositionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_positions",
        indexes = {
                @Index(name = "idx_job_positions_status", columnList = "status"),
                @Index(name = "idx_job_positions_department", columnList = "department"),
                @Index(name = "uk_job_positions_slug", columnList = "slug", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class JobPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "slug", length = 220, unique = true)
    private String slug;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "responsibilities", columnDefinition = "TEXT")
    private String responsibilities;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(name = "location", length = 160)
    private String location;

    @Column(name = "experience", length = 120)
    private String experience;

    @Column(name = "salary", length = 120)
    private String salary;

    @Column(name = "openings", nullable = false)
    private Integer openings;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobPositionStatus status;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "visible_to_external_applicants")
    private Boolean visibleToExternalApplicants = true;

    @Column(name = "deleted")
    private Boolean deleted = false;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = JobPositionStatus.OPEN;
        }
        if (openings == null || openings < 1) {
            openings = 1;
        }
        if (visibleToExternalApplicants == null) {
            visibleToExternalApplicants = true;
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (visibleToExternalApplicants == null) {
            visibleToExternalApplicants = true;
        }
        if (deleted == null) {
            deleted = false;
        }
    }
}
