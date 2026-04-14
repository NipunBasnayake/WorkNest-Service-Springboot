package com.worknest.tenant.dto.attendance;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AttendanceResponseDto {
    private Long id;
    private EmployeeSimpleDto employee;
    private LocalDate workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private Boolean late;
    private Boolean manualEntry;
    private String note;
    private EmployeeSimpleDto markedByEmployee;
    private Long workedMinutes;
    private AttendanceStatus status;
    private LocalDateTime createdAt;
}
