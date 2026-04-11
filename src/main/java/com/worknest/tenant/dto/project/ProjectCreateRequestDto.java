package com.worknest.tenant.dto.project;

import com.worknest.tenant.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProjectCreateRequestDto {

    @NotBlank(message = "Project name is required")
    @Size(max = 180, message = "Project name must not exceed 180 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    private ProjectStatus status;

    @Deprecated
    @Positive(message = "Creator employee ID must be positive")
    private Long createdByEmployeeId;
}
