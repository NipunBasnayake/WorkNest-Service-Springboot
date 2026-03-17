package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.settings.WorkspaceProfileResponseDto;
import com.worknest.tenant.dto.settings.WorkspaceProfileUpdateRequestDto;
import com.worknest.tenant.service.WorkspaceSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/settings")
public class TenantSettingsController {

    private final WorkspaceSettingsService workspaceSettingsService;

    public TenantSettingsController(WorkspaceSettingsService workspaceSettingsService) {
        this.workspaceSettingsService = workspaceSettingsService;
    }

    @GetMapping("/workspace")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<WorkspaceProfileResponseDto>> getWorkspaceProfile() {
        WorkspaceProfileResponseDto response = workspaceSettingsService.getWorkspaceProfile();
        return ResponseEntity.ok(ApiResponse.success("Workspace profile retrieved successfully", response));
    }

    @PatchMapping("/workspace")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<WorkspaceProfileResponseDto>> updateWorkspaceProfile(
            @Valid @RequestBody WorkspaceProfileUpdateRequestDto requestDto) {
        WorkspaceProfileResponseDto response = workspaceSettingsService.updateWorkspaceProfile(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Workspace profile updated successfully", response));
    }
}
