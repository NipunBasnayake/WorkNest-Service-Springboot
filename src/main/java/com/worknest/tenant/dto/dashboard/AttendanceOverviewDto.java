package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class AttendanceOverviewDto {
    private LocalDate date;
    private long totalRecords;
    private long presentCount;
    private long lateCount;
    private long halfDayCount;
    private long incompleteCount;
    private long absentCount;
    private double attendanceRatePercent;
}
