package com.worknest.tenant.dto.team;

import com.worknest.tenant.enums.TeamFunctionalRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberRoleUpdateRequestDto {

    @NotNull(message = "Functional role is required")
    private TeamFunctionalRole functionalRole;
}
