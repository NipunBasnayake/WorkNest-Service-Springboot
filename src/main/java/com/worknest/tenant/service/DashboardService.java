package com.worknest.tenant.service;

import com.worknest.tenant.dto.dashboard.EmployeeDashboardDto;
import com.worknest.tenant.dto.dashboard.HrDashboardDto;
import com.worknest.tenant.dto.dashboard.ManagerDashboardDto;
import com.worknest.tenant.dto.dashboard.TenantAdminDashboardDto;

public interface DashboardService {

    TenantAdminDashboardDto getTenantAdminSummary();

    ManagerDashboardDto getManagerSummary();

    HrDashboardDto getHrSummary();

    EmployeeDashboardDto getMySummary();
}
