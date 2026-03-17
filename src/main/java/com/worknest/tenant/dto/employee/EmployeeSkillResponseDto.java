package com.worknest.tenant.dto.employee;

import com.worknest.tenant.enums.SkillLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class EmployeeSkillResponseDto {
    private Long id;
    private Long employeeId;
    private String skillName;
    private SkillLevel skillLevel;
    private LocalDateTime createdAt;
}
