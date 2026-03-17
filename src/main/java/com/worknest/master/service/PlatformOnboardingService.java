package com.worknest.master.service;

import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;

public interface PlatformOnboardingService {

    TenantRegistrationResponseDto registerTenant(TenantRegistrationRequestDto requestDto);
}
