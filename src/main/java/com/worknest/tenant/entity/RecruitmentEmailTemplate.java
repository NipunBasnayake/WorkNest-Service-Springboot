package com.worknest.tenant.entity;

import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruitment_email_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recruitment_email_template_type", columnNames = "template_type")
})
@Getter
@Setter
@NoArgsConstructor
public class RecruitmentEmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 40)
    private RecruitmentEmailTemplateType type;

    @Column(name = "subject", nullable = false, length = 240)
    private String subject;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
