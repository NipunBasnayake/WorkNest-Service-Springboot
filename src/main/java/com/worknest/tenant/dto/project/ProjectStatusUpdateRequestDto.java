package com.worknest.tenant.dto.project;

import com.worknest.tenant.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectStatusUpdateRequestDto {

    @NotNull(message = "Status is required")
    private ProjectStatus status;
}
