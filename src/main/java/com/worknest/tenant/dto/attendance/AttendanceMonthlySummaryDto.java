package com.worknest.tenant.dto.attendance;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AttendanceMonthlySummaryDto {
    private Long employeeId;
    private int year;
    private int month;
    private long totalDays;
    private long presentDays;
    private long halfDays;
    private long incompleteDays;
}
