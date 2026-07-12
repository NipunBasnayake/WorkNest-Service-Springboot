package com.worknest.publicapi.service;

import com.worknest.publicapi.dto.PublicCareerJobDetailDto;
import com.worknest.publicapi.dto.PublicCareersResponseDto;

public interface PublicCareersService {
    PublicCareersResponseDto listPublishedCareers(String tenantSlug);

    PublicCareerJobDetailDto getPublishedCareer(String tenantSlug, String jobSlug);
}
