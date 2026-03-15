package com.worknest.tenant.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamUpdateRequestDto {

    @NotBlank(message = "Team name is required")
    @Size(max = 150, message = "Team name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Manager employee ID is required")
    private Long managerId;
}
