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
    private long myOverdueTasks;
    private long myPendingLeaves;
    private List<StatusCountDto> myTasksByStatus;
    private AttendanceMonthlySummaryDto currentMonthAttendance;
    private LocalDateTime generatedAt;
}
