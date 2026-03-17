package com.worknest.tenant.dto.employee;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class EmployeeResponseDto {
    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private Long platformUserId;
    private boolean accountProvisioned;
    private PlatformRole role;
    private String designation;
    private LocalDate joinedDate;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
