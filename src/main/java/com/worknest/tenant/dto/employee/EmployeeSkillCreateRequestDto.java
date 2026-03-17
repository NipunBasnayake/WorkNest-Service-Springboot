package com.worknest.tenant.dto.employee;

import com.worknest.tenant.enums.SkillLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeSkillCreateRequestDto {

    @NotBlank(message = "Skill name is required")
    @Size(max = 120, message = "Skill name must not exceed 120 characters")
    private String skillName;

    @NotNull(message = "Skill level is required")
    private SkillLevel skillLevel;
}
