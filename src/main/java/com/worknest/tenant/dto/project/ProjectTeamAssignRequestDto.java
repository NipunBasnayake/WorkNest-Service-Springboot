package com.worknest.tenant.dto.project;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTeamAssignRequestDto {

    @NotNull(message = "Team ID is required")
    private Long teamId;
}
