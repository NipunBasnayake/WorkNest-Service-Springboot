package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.dashboard.EmployeeDashboardDto;
import com.worknest.tenant.dto.dashboard.HrDashboardDto;
import com.worknest.tenant.dto.dashboard.ManagerDashboardDto;
import com.worknest.tenant.dto.dashboard.TenantAdminDashboardDto;
import com.worknest.tenant.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/tenant-admin")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TenantAdminDashboardDto>> getTenantAdminSummary() {
        TenantAdminDashboardDto response = dashboardService.getTenantAdminSummary();
        return ResponseEntity.ok(ApiResponse.success("Tenant admin dashboard retrieved successfully", response));
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ManagerDashboardDto>> getManagerSummary() {
        ManagerDashboardDto response = dashboardService.getManagerSummary();
        return ResponseEntity.ok(ApiResponse.success("Manager dashboard retrieved successfully", response));
    }

    @GetMapping("/hr")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<HrDashboardDto>> getHrSummary() {
        HrDashboardDto response = dashboardService.getHrSummary();
        return ResponseEntity.ok(ApiResponse.success("HR dashboard retrieved successfully", response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeDashboardDto>> getMySummary() {
        EmployeeDashboardDto response = dashboardService.getMySummary();
        return ResponseEntity.ok(ApiResponse.success("My dashboard retrieved successfully", response));
    }
}
