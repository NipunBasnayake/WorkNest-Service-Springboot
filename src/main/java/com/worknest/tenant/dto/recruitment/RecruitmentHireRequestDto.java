package com.worknest.tenant.dto.recruitment;

import com.worknest.common.enums.PlatformRole;
import com.worknest.tenant.enums.TeamFunctionalRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RecruitmentHireRequestDto {

    @Size(max = 30, message = "Employee code must not exceed 30 characters")
    private String employeeCode;

    private PlatformRole role = PlatformRole.EMPLOYEE;

    @Size(max = 120, message = "Designation must not exceed 120 characters")
    private String designation;

    @Size(max = 120, message = "Department must not exceed 120 characters")
    private String department;

    private LocalDate joinedDate;

    @Size(min = 8, max = 100, message = "Temporary password must be between 8 and 100 characters")
    private String temporaryPassword;

    @Positive(message = "Team id must be positive")
    private Long teamId;

    private TeamFunctionalRole teamFunctionalRole = TeamFunctionalRole.MEMBER;

    @DecimalMin(value = "0.00", message = "Salary cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "Salary must be a valid amount with up to 2 decimals")
    private BigDecimal salary;

    @Size(max = 5000, message = "Recruiter notes must not exceed 5000 characters")
    private String recruiterNotes;
}
