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
    private String department;
    private String description;
    private EmploymentType employmentType;
    private String location;
    private Integer openings;
    private JobPositionStatus status;
    private boolean published;
    private long applicationCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}