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
    private long activeTeams;
    private long totalProjects;
    private long activeProjects;
    private long totalTasks;
    private long completedTasks;
    private double taskCompletionRate;
    private long overdueTasks;
    private long pendingLeaveRequests;
    private long totalAnnouncements;
    private long myUnreadNotifications;
    private long todayAttendanceMarked;
    private long todayAttendanceAbsent;
    private List<StatusCountDto> projectsByStatus;
    private List<StatusCountDto> tasksByStatus;
    private List<StatusCountDto> leavesByStatus;
    private List<DashboardAnnouncementItemDto> recentAnnouncements;
    private List<DashboardNotificationItemDto> recentNotifications;
    private AttendanceOverviewDto todayAttendance;
    private LocalDateTime generatedAt;
}
