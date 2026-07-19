package com.worknest.master.service;

import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface PlatformOnboardingService {

    TenantRegistrationResponseDto registerTenant(TenantRegistrationRequestDto requestDto);

    TenantRegistrationResponseDto registerTenant(
            TenantRegistrationRequestDto requestDto,
            MultipartFile logo,
            String idempotencyKey);
}
