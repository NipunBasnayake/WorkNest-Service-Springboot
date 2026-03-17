package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.tenant.dto.attendance.*;
import com.worknest.tenant.entity.AttendanceRecord;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AttendanceStatus;
import com.worknest.tenant.repository.AttendanceRecordRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.service.AttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantDtoMapper tenantDtoMapper;

    public AttendanceServiceImpl(
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            TenantDtoMapper tenantDtoMapper) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.tenantDtoMapper = tenantDtoMapper;
    }

    @Override
    public AttendanceResponseDto checkIn(AttendanceCheckInRequestDto requestDto) {
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());
        LocalDate workDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate)
                .orElseGet(() -> {
                    AttendanceRecord newRecord = new AttendanceRecord();
                    newRecord.setEmployee(employee);
                    newRecord.setWorkDate(workDate);
                    return newRecord;
                });

        if (record.getCheckIn() != null) {
            throw new BadRequestException("Employee already checked in for date: " + workDate);
        }

        record.setCheckIn(now);
        record.setStatus(AttendanceStatus.INCOMPLETE);

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        return toAttendanceResponse(saved);
    }

    @Override
    public AttendanceResponseDto checkOut(AttendanceCheckOutRequestDto requestDto) {
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());
        LocalDate workDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in record found for today"));

        if (record.getCheckIn() == null) {
            throw new BadRequestException("Check-in is required before check-out");
        }

        if (record.getCheckOut() != null) {
            throw new BadRequestException("Employee already checked out for date: " + workDate);
        }

        if (now.isBefore(record.getCheckIn())) {
            throw new BadRequestException("Check-out time cannot be before check-in time");
        }

        record.setCheckOut(now);
        record.setStatus(resolveAttendanceStatus(record.getCheckIn(), record.getCheckOut()));

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        return toAttendanceResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AttendanceResponseDto> getByEmployee(Long employeeId) {
        getEmployeeOrThrow(employeeId);
        return attendanceRecordRepository.findByEmployeeIdOrderByWorkDateDesc(employeeId).stream()
                .map(this::toAttendanceResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AttendanceResponseDto> getByDate(LocalDate workDate) {
        return attendanceRecordRepository.findByWorkDateOrderByEmployeeIdAsc(workDate).stream()
                .map(this::toAttendanceResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AttendanceDailySummaryDto getDailySummary(LocalDate workDate) {
        List<AttendanceRecord> records = attendanceRecordRepository.findByWorkDateOrderByEmployeeIdAsc(workDate);

        long presentCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long halfDayCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
        long incompleteCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.INCOMPLETE).count();

        return AttendanceDailySummaryDto.builder()
                .workDate(workDate)
                .totalRecords(records.size())
                .presentCount(presentCount)
                .halfDayCount(halfDayCount)
                .incompleteCount(incompleteCount)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AttendanceMonthlySummaryDto getMonthlySummary(Long employeeId, int year, int month) {
        getEmployeeOrThrow(employeeId);
        if (month < 1 || month > 12) {
            throw new BadRequestException("Month must be between 1 and 12");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        List<AttendanceRecord> records = attendanceRecordRepository
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);

        long presentDays = records.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long halfDays = records.stream().filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
        long incompleteDays = records.stream().filter(r -> r.getStatus() == AttendanceStatus.INCOMPLETE).count();

        return AttendanceMonthlySummaryDto.builder()
                .employeeId(employeeId)
                .year(year)
                .month(month)
                .totalDays(records.size())
                .presentDays(presentDays)
                .halfDays(halfDays)
                .incompleteDays(incompleteDays)
                .build();
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    private Employee getActiveEmployeeOrThrow(Long employeeId) {
        Employee employee = getEmployeeOrThrow(employeeId);
        if (employee.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Employee is not active: " + employeeId);
        }
        return employee;
    }

    private AttendanceStatus resolveAttendanceStatus(LocalDateTime checkIn, LocalDateTime checkOut) {
        long workedMinutes = Duration.between(checkIn, checkOut).toMinutes();
        if (workedMinutes < 4 * 60) {
            return AttendanceStatus.HALF_DAY;
        }
        return AttendanceStatus.PRESENT;
    }

    private AttendanceResponseDto toAttendanceResponse(AttendanceRecord attendanceRecord) {
        return AttendanceResponseDto.builder()
                .id(attendanceRecord.getId())
                .employee(tenantDtoMapper.toEmployeeSimple(attendanceRecord.getEmployee()))
                .workDate(attendanceRecord.getWorkDate())
                .checkIn(attendanceRecord.getCheckIn())
                .checkOut(attendanceRecord.getCheckOut())
                .status(attendanceRecord.getStatus())
                .createdAt(attendanceRecord.getCreatedAt())
                .build();
    }
}
