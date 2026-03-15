package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.analytics.*;
import com.worknest.tenant.dto.dashboard.ProjectTaskProgressDto;
import com.worknest.tenant.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/tasks/by-assignee")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<TaskAssigneeSummaryDto>>> getTaskCountByAssignee() {
        List<TaskAssigneeSummaryDto> response = analyticsService.getTaskCountByAssignee();
        return ResponseEntity.ok(ApiResponse.success("Task summary by assignee retrieved", response));
    }

    @GetMapping("/tasks/by-project")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<TaskProjectSummaryDto>>> getTaskCountByProject() {
        List<TaskProjectSummaryDto> response = analyticsService.getTaskCountByProject();
        return ResponseEntity.ok(ApiResponse.success("Task summary by project retrieved", response));
    }

    @GetMapping("/projects/progress")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectTaskProgressDto>>> getProjectProgressSummary() {
        List<ProjectTaskProgressDto> response = analyticsService.getProjectProgressSummary();
        return ResponseEntity.ok(ApiResponse.success("Project progress summary retrieved", response));
    }

    @GetMapping("/leaves/trend")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<LeaveTrendPointDto>>> getLeaveTrend(
            @RequestParam int year) {
        List<LeaveTrendPointDto> response = analyticsService.getLeaveTrend(year);
        return ResponseEntity.ok(ApiResponse.success("Leave trend retrieved", response));
    }

    @GetMapping("/attendance/trend")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<AttendanceTrendPointDto>>> getAttendanceTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<AttendanceTrendPointDto> response = analyticsService.getAttendanceTrend(fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Attendance trend retrieved", response));
    }

    @GetMapping("/employees/role-distribution")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<EmployeeRoleDistributionDto>>> getEmployeeRoleDistribution() {
        List<EmployeeRoleDistributionDto> response = analyticsService.getEmployeeRoleDistribution();
        return ResponseEntity.ok(ApiResponse.success("Employee role distribution retrieved", response));
    }

    @GetMapping("/employees/designation-distribution")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<EmployeeDesignationDistributionDto>>> getEmployeeDesignationDistribution() {
        List<EmployeeDesignationDistributionDto> response = analyticsService.getEmployeeDesignationDistribution();
        return ResponseEntity.ok(ApiResponse.success("Employee designation distribution retrieved", response));
    }
}
