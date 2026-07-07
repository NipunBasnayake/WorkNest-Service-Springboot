package com.worknest.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "candidates",
        indexes = {
                @Index(name = "idx_candidates_email", columnList = "email"),
                @Index(name = "idx_candidates_full_name", columnList = "full_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 180)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "current_title", length = 160)
    private String currentTitle;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "source", length = 120)
    private String source;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "resume_file_url", length = 1000)
    private String resumeFileUrl;

    @Column(name = "resume_mime_type", length = 120)
    private String resumeMimeType;

    @Column(name = "resume_file_size_bytes")
    private Long resumeFileSizeBytes;

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