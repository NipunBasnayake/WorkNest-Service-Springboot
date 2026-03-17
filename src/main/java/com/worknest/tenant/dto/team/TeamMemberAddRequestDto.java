package com.worknest.tenant.dto.team;

import com.worknest.tenant.enums.TeamFunctionalRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberAddRequestDto {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    private TeamFunctionalRole functionalRole;
}
