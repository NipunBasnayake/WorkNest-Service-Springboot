package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.dto.PlatformTenantUpdateRequestDto;
import com.worknest.master.dto.TenantStatusUpdateRequestDto;
import com.worknest.master.service.PlatformTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@Slf4j
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<PlatformTenantResponseDto>>> getAllTenants() {
        log.info("Request received to get all tenants");
        List<PlatformTenantResponseDto> tenants = platformTenantService.getAllTenants();
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved successfully", tenants));
    }

    @GetMapping("/{tenantKey}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantResponseDto>> getTenantByKey(@PathVariable("tenantKey") String tenantKey) {
        log.info("Request received to get tenant: {}", tenantKey);
        PlatformTenantResponseDto tenant = platformTenantService.getTenantByKey(tenantKey);
        return ResponseEntity.ok(ApiResponse.success("Tenant retrieved successfully", tenant));
    }

    @PatchMapping("/{tenantKey}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantResponseDto>> updateTenantStatus(
            @PathVariable("tenantKey") String tenantKey,
            @Valid @RequestBody TenantStatusUpdateRequestDto requestDto) {
        log.info("Request received to update tenant status for {} to {}", tenantKey, requestDto.getStatus());
        PlatformTenantResponseDto tenant = platformTenantService.updateTenantStatus(tenantKey, requestDto.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Tenant status updated successfully", tenant));
    }

    @PutMapping("/{tenantKey}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformTenantResponseDto>> updateTenant(
            @PathVariable("tenantKey") String tenantKey,
            @Valid @RequestBody PlatformTenantUpdateRequestDto requestDto) {
        log.info("Request received to update tenant metadata for {}", tenantKey);
        PlatformTenantResponseDto tenant = platformTenantService.updateTenant(tenantKey, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Tenant updated successfully", tenant));
    }

    @DeleteMapping("/{tenantKey}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTenant(@PathVariable("tenantKey") String tenantKey) {
        log.info("Request received to suspend tenant {}", tenantKey);
        platformTenantService.deleteTenant(tenantKey);
        return ResponseEntity.ok(ApiResponse.success("Tenant suspended successfully"));
    }
}

