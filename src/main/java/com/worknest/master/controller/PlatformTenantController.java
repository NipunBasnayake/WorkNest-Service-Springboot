package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.service.PlatformTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
@Slf4j
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformTenantResponseDto>>> getAllTenants() {
        log.info("Request received to get all tenants");
        List<PlatformTenantResponseDto> tenants = platformTenantService.getAllTenants();
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved successfully", tenants));
    }

    @GetMapping("/{tenantKey}")
    public ResponseEntity<ApiResponse<PlatformTenantResponseDto>> getTenantByKey(@PathVariable String tenantKey) {
        log.info("Request received to get tenant: {}", tenantKey);
        PlatformTenantResponseDto tenant = platformTenantService.getTenantByKey(tenantKey);
        return ResponseEntity.ok(ApiResponse.success("Tenant retrieved successfully", tenant));
    }
}

