package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class TenantAdminDashboardDto {
    private long totalEmployees;
    private long activeEmployees;
    private long inactiveEmployees;
    private long totalTeams;
    private long totalProjects;
    private long totalTasks;
    private long overdueTasks;
    private long pendingLeaveRequests;
    private List<StatusCountDto> projectsByStatus;
    private List<StatusCountDto> tasksByStatus;
    private List<StatusCountDto> leavesByStatus;
    private AttendanceOverviewDto todayAttendance;
    private LocalDateTime generatedAt;
}
