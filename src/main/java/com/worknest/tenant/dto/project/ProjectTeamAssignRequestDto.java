package com.worknest.tenant.dto.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectTeamAssignRequestDto {

    @NotNull(message = "Team ID is required")
    @Positive(message = "Team ID must be positive")
    private Long teamId;
}
