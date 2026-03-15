package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class HrDashboardDto {
    private long totalEmployees;
    private long activeEmployees;
    private long pendingLeaveRequests;
    private List<StatusCountDto> leavesByStatus;
    private AttendanceOverviewDto todayAttendance;
    private List<StatusCountDto> employeesByRole;
    private LocalDateTime generatedAt;
}
