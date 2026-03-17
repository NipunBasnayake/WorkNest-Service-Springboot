package com.worknest.tenant.entity;

import com.worknest.tenant.enums.SkillLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_skills",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_skills_employee_skill", columnNames = {"employee_id", "skill_name"})
        },
        indexes = {
                @Index(name = "idx_employee_skills_employee", columnList = "employee_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "skill_name", nullable = false, length = 120)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false, length = 20)
    private SkillLevel skillLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
