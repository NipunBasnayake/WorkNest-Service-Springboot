package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/{tenantSlug}")
public class PublicTenantController {

    private final MasterTenantLookupService masterTenantLookupService;

    public PublicTenantController(MasterTenantLookupService masterTenantLookupService) {
        this.masterTenantLookupService = masterTenantLookupService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantBootstrapInfo(@PathVariable("tenantSlug") String tenantSlug) {
        PlatformTenant tenant = masterTenantLookupService.findBySlug(tenantSlug).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("companyName", tenant.getCompanyName());
        metadata.put("tenantKey", tenant.getTenantKey());
        metadata.put("slug", tenant.getSlug());
        metadata.put("status", tenant.getStatus());

        return ResponseEntity.ok(ApiResponse.success("Tenant metadata retrieved successfully", metadata));
    }
}
