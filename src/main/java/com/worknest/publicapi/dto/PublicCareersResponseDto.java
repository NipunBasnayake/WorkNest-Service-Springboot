package com.worknest.publicapi.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PublicCareersResponseDto {
    private PublicCompanyDto company;
    private List<PublicCareerJobSummaryDto> jobs;
}
