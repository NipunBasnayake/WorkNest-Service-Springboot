package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DashboardAttendanceSnapshotDto {
    private LocalDate workDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private AttendanceStatus status;
}
