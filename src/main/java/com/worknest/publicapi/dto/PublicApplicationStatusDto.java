package com.worknest.publicapi.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PublicApplicationStatusDto {
    private String referenceNumber;
    private String vacancyTitle;
    private String jobSlug;
    private String status;
    private PublicCompanyDto company;
    private LocalDateTime submittedDate;
}
