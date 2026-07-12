package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.EmploymentType;
import com.worknest.tenant.enums.JobPositionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobPositionUpdateRequestDto {

    @NotBlank(message = "Job title is required")
    @Size(max = 180, message = "Job title must not exceed 180 characters")
    private String title;

    @Size(max = 120, message = "Department must not exceed 120 characters")
    private String department;

    @Size(max = 500, message = "Summary must not exceed 500 characters")
    private String summary;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 8000, message = "Responsibilities must not exceed 8000 characters")
    private String responsibilities;

    @Size(max = 8000, message = "Requirements must not exceed 8000 characters")
    private String requirements;

    @Size(max = 8000, message = "Benefits must not exceed 8000 characters")
    private String benefits;

    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;

    @Size(max = 160, message = "Location must not exceed 160 characters")
    private String location;

    @Size(max = 120, message = "Experience must not exceed 120 characters")
    private String experience;

    @Size(max = 120, message = "Salary must not exceed 120 characters")
    private String salary;

    @NotNull(message = "Openings is required")
    @Min(value = 1, message = "Openings must be at least 1")
    private Integer openings;

    @NotNull(message = "Status is required")
    private JobPositionStatus status;

    @NotNull(message = "Published flag is required")
    private Boolean published;

    private Boolean visibleToExternalApplicants;

    private LocalDateTime expiresAt;
}
