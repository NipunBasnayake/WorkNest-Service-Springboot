package com.worknest.publicapi.service;

import com.worknest.publicapi.dto.PublicApplicationRequestDto;
import com.worknest.publicapi.dto.PublicApplicationResponseDto;
import com.worknest.publicapi.dto.PublicApplicationStatusDto;

public interface PublicCandidateApplicationService {

    PublicApplicationResponseDto apply(String tenantSlug, String jobSlug, PublicApplicationRequestDto requestDto);

    PublicApplicationStatusDto getStatus(String tenantSlug, String referenceNumber);
}
