package com.worknest.tenant.entity;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "employees",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employees_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_employees_code", columnNames = "employee_code")
        },
        indexes = {
                @Index(name = "idx_employees_role_status", columnList = "role,status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "platform_user_id")
    private Long platformUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private PlatformRole role;

    @Column(name = "designation", length = 120)
    private String designation;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "salary", precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (joinedDate == null) {
            joinedDate = LocalDate.now();
        }
        if (role == null) {
            role = PlatformRole.EMPLOYEE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
