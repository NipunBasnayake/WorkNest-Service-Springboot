package com.worknest.tenant.service;

import com.worknest.tenant.dto.analytics.*;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    BusinessIntelligenceReportDto getBusinessIntelligenceReport(
            String tenantSlug, LocalDate fromDate, LocalDate toDate, String department,
            Long teamId, Long employeeId, Long projectId, String taskStatus,
            String recruitmentStatus, String attendancePeriod, String leaveType);

    List<TaskAssigneeSummaryDto> getTaskCountByAssignee();

    List<TaskProjectSummaryDto> getTaskCountByProject();

    List<com.worknest.tenant.dto.dashboard.ProjectTaskProgressDto> getProjectProgressSummary();

    List<LeaveTrendPointDto> getLeaveTrend(int year);

    List<AttendanceTrendPointDto> getAttendanceTrend(LocalDate fromDate, LocalDate toDate);

    List<EmployeeRoleDistributionDto> getEmployeeRoleDistribution();

    List<EmployeeDesignationDistributionDto> getEmployeeDesignationDistribution();
}
