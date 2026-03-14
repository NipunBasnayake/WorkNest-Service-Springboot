package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;
import com.worknest.master.service.PlatformOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/onboarding")
public class PlatformOnboardingController {

    private final PlatformOnboardingService platformOnboardingService;

    public PlatformOnboardingController(PlatformOnboardingService platformOnboardingService) {
        this.platformOnboardingService = platformOnboardingService;
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantRegistrationResponseDto>> registerTenant(
            @Valid @RequestBody TenantRegistrationRequestDto requestDto) {

        TenantRegistrationResponseDto responseDto = platformOnboardingService.registerTenant(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant onboarded successfully", responseDto));
    }
}
