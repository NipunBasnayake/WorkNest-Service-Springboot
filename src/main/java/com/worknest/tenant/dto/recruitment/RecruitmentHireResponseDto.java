package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.employee.EmployeeResponseDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RecruitmentHireResponseDto {
    private CandidateApplicationResponseDto application;
    private EmployeeResponseDto employee;
    private Long teamId;
    private String teamName;
    private boolean accountProvisioned;
    private String temporaryPassword;
}
