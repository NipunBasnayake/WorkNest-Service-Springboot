package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.EmploymentType;
import com.worknest.tenant.enums.JobPositionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class JobPositionResponseDto {
    private Long id;
    private String title;
    private String slug;
    private String department;
    private String summary;
    private String description;
    private String responsibilities;
    private String requirements;
    private String benefits;
    private EmploymentType employmentType;
    private String location;
    private String experience;
    private String salary;
    private Integer openings;
    private JobPositionStatus status;
    private boolean published;
    private Boolean visibleToExternalApplicants;
    private LocalDateTime expiresAt;
    private long applicationCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
