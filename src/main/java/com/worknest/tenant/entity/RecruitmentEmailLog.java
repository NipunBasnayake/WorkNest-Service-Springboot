package com.worknest.tenant.entity;

import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruitment_email_logs", indexes = {
        @Index(name = "idx_recruitment_email_log_application", columnList = "application_id,sent_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RecruitmentEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CandidateApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 40)
    private RecruitmentEmailTemplateType templateType;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "subject", nullable = false, length = 240)
    private String subject;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        sentAt = LocalDateTime.now();
        if (deliveryStatus == null) {
            deliveryStatus = "QUEUED";
        }
    }
}
