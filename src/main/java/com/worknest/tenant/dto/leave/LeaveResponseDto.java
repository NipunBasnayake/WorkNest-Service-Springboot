package com.worknest.tenant.dto.leave;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
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
public class LeaveResponseDto {
    private Long id;
    private EmployeeSimpleDto employee;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LeaveStatus status;
    private EmployeeSimpleDto approver;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
