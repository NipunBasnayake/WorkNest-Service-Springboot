package com.worknest.publicapi.dto;

import com.worknest.tenant.enums.EmploymentType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PublicCareerJobSummaryDto {
    private String slug;
    private String title;
    private String department;
    private EmploymentType employmentType;
    private String location;
    private String experience;
    private String salary;
    private String summary;
    private LocalDateTime postedDate;
    private LocalDateTime expiry;
}
