package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;
import com.worknest.master.service.PlatformOnboardingService;
import com.worknest.master.service.OnboardingThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/onboarding")
public class PlatformOnboardingController {

    private final PlatformOnboardingService platformOnboardingService;
    private final OnboardingThrottleService onboardingThrottleService;

    public PlatformOnboardingController(
            PlatformOnboardingService platformOnboardingService,
            OnboardingThrottleService onboardingThrottleService) {
        this.platformOnboardingService = platformOnboardingService;
        this.onboardingThrottleService = onboardingThrottleService;
    }

    @PostMapping(value = "/tenants", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TenantRegistrationResponseDto>> registerTenant(
            @Valid @RequestBody TenantRegistrationRequestDto requestDto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        onboardingThrottleService.checkAndRecord(request.getRemoteAddr());
        TenantRegistrationResponseDto responseDto = platformOnboardingService.registerTenant(
                requestDto,
                null,
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Tenant registration accepted. Provisioning in progress.", responseDto));
    }

    @PostMapping(value = "/tenants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TenantRegistrationResponseDto>> registerTenantWithBranding(
            @Valid @RequestPart("data") TenantRegistrationRequestDto requestDto,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        onboardingThrottleService.checkAndRecord(request.getRemoteAddr());
        TenantRegistrationResponseDto responseDto = platformOnboardingService.registerTenant(
                requestDto,
                logo,
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Tenant registration accepted. Provisioning in progress.", responseDto));
    }
}
