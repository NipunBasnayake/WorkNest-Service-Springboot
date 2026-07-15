package com.worknest.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruitment_application_events", indexes = {
        @Index(name = "idx_recruitment_event_application", columnList = "application_id,occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RecruitmentApplicationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CandidateApplication application;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "detail", length = 1000)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_employee_id")
    private Employee actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        occurredAt = LocalDateTime.now();
    }
}
