package com.worknest.tenant.dto.employee;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EmployeeSkillResponseDto {
    private Long id;
    private Long employeeId;
    private String skillName;
}
