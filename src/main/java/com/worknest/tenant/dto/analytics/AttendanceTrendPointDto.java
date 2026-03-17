package com.worknest.tenant.dto.analytics;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class AttendanceTrendPointDto {
    private LocalDate workDate;
    private long presentCount;
    private long halfDayCount;
    private long incompleteCount;
}
