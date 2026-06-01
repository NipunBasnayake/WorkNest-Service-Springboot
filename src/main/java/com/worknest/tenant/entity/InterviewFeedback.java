package com.worknest.tenant.entity;

import com.worknest.tenant.enums.InterviewRecommendation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "interview_feedback",
        indexes = {
                @Index(name = "idx_interview_feedback_interview", columnList = "interview_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class InterviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false, unique = true)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_employee_id")
    private Employee reviewer;

    @Column(name = "rating")
    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", length = 20)
    private InterviewRecommendation recommendation;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "concerns", columnDefinition = "TEXT")
    private String concerns;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}