package com.worknest.publicapi.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PublicCompanyDto {
    private String tenantSlug;
    private String companyName;
    private String logoUrl;
    private String about;
}
