package com.worknest.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "employee_skills",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_skills_employee_skill", columnNames = {"employee_id", "skill_name"})
        },
        indexes = {
                @Index(name = "idx_employee_skills_employee", columnList = "employee_id"),
                @Index(name = "idx_employee_skills_name", columnList = "skill_name")
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
}
