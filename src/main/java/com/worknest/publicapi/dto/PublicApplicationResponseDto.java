package com.worknest.publicapi.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PublicApplicationResponseDto {
    private String referenceNumber;
    private String vacancyTitle;
    private String jobSlug;
    private PublicCompanyDto company;
    private LocalDateTime submittedDate;
    private String message;
}
