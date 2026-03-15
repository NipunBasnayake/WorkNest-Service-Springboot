package com.worknest.tenant.entity;

import com.worknest.tenant.enums.TeamFunctionalRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "team_members",
        indexes = {
                @Index(name = "idx_team_members_employee", columnList = "employee_id"),
                @Index(name = "idx_team_members_left_at", columnList = "left_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "functional_role", nullable = false, length = 40)
    private TeamFunctionalRole functionalRole;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
        if (functionalRole == null) {
            functionalRole = TeamFunctionalRole.MEMBER;
        }
    }
}
