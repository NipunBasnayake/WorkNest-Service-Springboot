package com.worknest.tenant.dto.attendance;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class AttendanceDailySummaryDto {
    private LocalDate workDate;
    private long totalRecords;
    private long presentCount;
    private long lateCount;
    private long halfDayCount;
    private long incompleteCount;
    private long absentCount;
}
