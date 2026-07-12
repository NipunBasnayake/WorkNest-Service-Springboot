package com.worknest.tenant.entity;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "candidate_applications",
        indexes = {
                @Index(name = "idx_candidate_applications_job_status", columnList = "job_position_id,status"),
                @Index(name = "idx_candidate_applications_candidate", columnList = "candidate_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CandidateApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Column(name = "reference_number", length = 30, unique = true)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CandidatePipelineStatus status;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Column(name = "expected_salary")
    private BigDecimal expectedSalary;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "source", length = 120)
    private String source;

    @Column(name = "recruiter_notes", columnDefinition = "TEXT")
    private String recruiterNotes;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_employee_id")
    private Employee createdBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "hired_at")
    private LocalDateTime hiredAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        appliedAt = now;
        updatedAt = now;
        if (status == null) {
            status = CandidatePipelineStatus.APPLIED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
