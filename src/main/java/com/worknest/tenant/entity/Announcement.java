package com.worknest.tenant.entity;

import com.worknest.tenant.enums.AnnouncementCreatorRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String legacyMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Employee createdBy;

    @Column(name = "created_by_name", nullable = false, length = 220)
    private String createdByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false, length = 30)
    private AnnouncementCreatorRole createdByRole;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        syncContentColumns();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncContentColumns();
    }

    public String getMessage() {
        return hasText(content) ? content : legacyMessage;
    }

    public void setMessage(String message) {
        this.legacyMessage = message;
        if (!hasText(this.content)) {
            this.content = message;
        }
    }

    public String getContent() {
        return hasText(content) ? content : legacyMessage;
    }

    public void setContent(String content) {
        this.content = content;
        this.legacyMessage = content;
    }

    private void syncContentColumns() {
        if (!hasText(content) && hasText(legacyMessage)) {
            content = legacyMessage;
        }
        if (!hasText(legacyMessage) && hasText(content)) {
            legacyMessage = content;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
