package com.worknest.tenant.service;

import com.worknest.tenant.dto.attendance.*;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    AttendanceResponseDto checkIn(AttendanceCheckInRequestDto requestDto);

    AttendanceResponseDto checkOut(AttendanceCheckOutRequestDto requestDto);

    List<AttendanceResponseDto> getByEmployee(Long employeeId);

    List<AttendanceResponseDto> getByDate(LocalDate workDate);

    AttendanceDailySummaryDto getDailySummary(LocalDate workDate);

    AttendanceMonthlySummaryDto getMonthlySummary(Long employeeId, int year, int month);
}
