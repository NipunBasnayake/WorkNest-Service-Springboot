package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.LeaveType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DashboardLeaveItemDto {
    private Long id;
    private LeaveType leaveType;
    private LeaveStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
}
