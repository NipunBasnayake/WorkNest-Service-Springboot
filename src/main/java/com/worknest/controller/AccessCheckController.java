package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.tenant.context.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AccessCheckController {

    @GetMapping("/api/platform/access-check")
    public ApiResponse<Map<String, String>> platformAccessCheck(Authentication authentication) {
        return ApiResponse.success("Platform access granted", buildAuthSummary(authentication));
    }

    @GetMapping("/api/tenant/access-check")
    public ApiResponse<Map<String, String>> tenantAccessCheck(Authentication authentication) {
        Map<String, String> summary = buildAuthSummary(authentication);
        summary.put("tenantContext", TenantContext.getTenantId());
        return ApiResponse.success("Tenant access granted", summary);
    }

    private Map<String, String> buildAuthSummary(Authentication authentication) {
        Map<String, String> result = new HashMap<>();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
            result.put("email", principal.getUsername());
            result.put("role", principal.getRole().name());
            result.put("tenantKey", principal.getTenantKey());
            result.put("authorities", principal.getAuthorities().toString());
            return result;
        }
        result.put("authenticated", "false");
        return result;
    }
}
