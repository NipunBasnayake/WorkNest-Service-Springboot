package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.attendance.*;
import com.worknest.tenant.dto.employee.EmployeeResponseDto;
import com.worknest.tenant.service.AttendanceService;
import com.worknest.tenant.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;

    public AttendanceController(AttendanceService attendanceService, EmployeeService employeeService) {
        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
    }

    @PostMapping("/my/check-in")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> myCheckIn() {
        EmployeeResponseDto me = employeeService.getMyProfile();
        AttendanceCheckInRequestDto requestDto = new AttendanceCheckInRequestDto();
        requestDto.setEmployeeId(me.getId());
        AttendanceResponseDto response = attendanceService.checkIn(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Check-in completed", response));
    }

    @PostMapping("/my/check-out")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> myCheckOut() {
        EmployeeResponseDto me = employeeService.getMyProfile();
        AttendanceCheckOutRequestDto requestDto = new AttendanceCheckOutRequestDto();
        requestDto.setEmployeeId(me.getId());
        AttendanceResponseDto response = attendanceService.checkOut(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Check-out completed", response));
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> checkIn(
            @Valid @RequestBody AttendanceCheckInRequestDto requestDto) {
        AttendanceResponseDto response = attendanceService.checkIn(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Check-in recorded", response));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> checkOut(
            @Valid @RequestBody AttendanceCheckOutRequestDto requestDto) {
        AttendanceResponseDto response = attendanceService.checkOut(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Check-out recorded", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AttendanceResponseDto>>> myAttendance() {
        EmployeeResponseDto me = employeeService.getMyProfile();
        List<AttendanceResponseDto> response = attendanceService.getByEmployee(me.getId());
        return ResponseEntity.ok(ApiResponse.success("My attendance records retrieved", response));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponseDto>>> getByEmployee(@PathVariable Long employeeId) {
        List<AttendanceResponseDto> response = attendanceService.getByEmployee(employeeId);
        return ResponseEntity.ok(ApiResponse.success("Attendance records retrieved", response));
    }

    @GetMapping("/date/{workDate}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponseDto>>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        List<AttendanceResponseDto> response = attendanceService.getByDate(workDate);
        return ResponseEntity.ok(ApiResponse.success("Attendance by date retrieved", response));
    }

    @GetMapping("/summary/daily")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<AttendanceDailySummaryDto>> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        AttendanceDailySummaryDto response = attendanceService.getDailySummary(workDate);
        return ResponseEntity.ok(ApiResponse.success("Daily attendance summary retrieved", response));
    }

    @GetMapping("/summary/monthly")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<AttendanceMonthlySummaryDto>> getMonthlySummary(
            @RequestParam Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {
        AttendanceMonthlySummaryDto response = attendanceService.getMonthlySummary(employeeId, year, month);
        return ResponseEntity.ok(ApiResponse.success("Monthly attendance summary retrieved", response));
    }

    @GetMapping("/summary/my-monthly")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceMonthlySummaryDto>> getMyMonthlySummary(
            @RequestParam int year,
            @RequestParam int month) {
        EmployeeResponseDto me = employeeService.getMyProfile();
        AttendanceMonthlySummaryDto response = attendanceService.getMonthlySummary(me.getId(), year, month);
        return ResponseEntity.ok(ApiResponse.success("My monthly attendance summary retrieved", response));
    }
}
