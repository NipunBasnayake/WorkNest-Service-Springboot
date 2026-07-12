package com.worknest.publicapi.dto;

import com.worknest.tenant.enums.EmploymentType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PublicCareerJobDetailDto {
    private PublicCompanyDto company;
    private String slug;
    private String title;
    private String department;
    private EmploymentType employmentType;
    private String location;
    private String experience;
    private String salary;
    private String summary;
    private String description;
    private String responsibilities;
    private String requirements;
    private String benefits;
    private LocalDateTime postedDate;
    private LocalDateTime expiry;
}
