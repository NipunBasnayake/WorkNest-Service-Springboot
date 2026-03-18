package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.dto.attendance.AttendanceMonthlySummaryDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class EmployeeDashboardDto {
    private Long employeeId;
    private long myTasksTotal;
    private long myCompletedTasks;
    private double myTaskCompletionRate;
    private long myOverdueTasks;
    private long myDueSoonTasks;
    private long myPendingLeaves;
    private List<StatusCountDto> myTasksByStatus;
    private List<StatusCountDto> myLeavesByStatus;
    private List<DashboardTaskItemDto> dueSoonTasks;
    private List<DashboardTaskItemDto> overdueTaskItems;
    private List<DashboardLeaveItemDto> recentLeaveRequests;
    private DashboardAttendanceSnapshotDto latestAttendance;
    private List<DashboardAnnouncementItemDto> recentAnnouncements;
    private long myUnreadNotifications;
    private List<DashboardNotificationItemDto> recentNotifications;
    private AttendanceMonthlySummaryDto currentMonthAttendance;
    private LocalDateTime generatedAt;
}
