package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.EmploymentType;
import com.worknest.tenant.enums.JobPositionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobPositionCreateRequestDto {

    @NotBlank(message = "Job title is required")
    @Size(max = 180, message = "Job title must not exceed 180 characters")
    private String title;

    @Size(max = 120, message = "Department must not exceed 120 characters")
    private String department;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;

    @Size(max = 160, message = "Location must not exceed 160 characters")
    private String location;

    @NotNull(message = "Openings is required")
    @Min(value = 1, message = "Openings must be at least 1")
    private Integer openings;

    private JobPositionStatus status;

    private Boolean published;
}