package com.worknest.tenant.dto.team;

import com.worknest.tenant.enums.TeamFunctionalRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberAddRequestDto {

    @NotNull(message = "Employee ID is required")
    @Positive(message = "Employee ID must be positive")
    private Long employeeId;

    private TeamFunctionalRole functionalRole;
}
