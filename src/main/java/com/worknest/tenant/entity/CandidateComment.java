package com.worknest.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "candidate_comments",
        indexes = {
                @Index(name = "idx_candidate_comments_candidate", columnList = "candidate_id"),
                @Index(name = "idx_candidate_comments_application", columnList = "application_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CandidateComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private CandidateApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_employee_id")
    private Employee author;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
