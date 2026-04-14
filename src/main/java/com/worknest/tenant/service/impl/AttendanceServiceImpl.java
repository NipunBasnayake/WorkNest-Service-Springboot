package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.attendance.*;
import com.worknest.tenant.entity.AttendanceRecord;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AttendanceStatus;
import com.worknest.tenant.repository.AttendanceRecordRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.service.AttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(transactionManager = "transactionManager")
public class AttendanceServiceImpl implements AttendanceService {

    private static final LocalTime LATE_CUTOFF = LocalTime.of(9, 15);
    private static final long HALF_DAY_MINUTES = 4 * 60L;
    private static final long FULL_DAY_MINUTES = 8 * 60L;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantDtoMapper tenantDtoMapper;
    private final EmailNotificationService emailNotificationService;
    private final SecurityUtils securityUtils;
    private final Clock clock;

    public AttendanceServiceImpl(
            AttendanceRecordRepository attendanceRecordRepository,
            EmployeeRepository employeeRepository,
            TenantDtoMapper tenantDtoMapper,
            EmailNotificationService emailNotificationService,
            SecurityUtils securityUtils,
            Clock clock) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.tenantDtoMapper = tenantDtoMapper;
        this.emailNotificationService = emailNotificationService;
        this.securityUtils = securityUtils;
        this.clock = clock;
    }

    @Override
    public AttendanceResponseDto checkIn(AttendanceCheckInRequestDto requestDto) {
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());
        LocalDate workDate = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

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
        record.setLate(isLateArrival(now));
        record.setManualEntry(requestDto.isManualEntry());
        record.setNote(normalizeNote(requestDto.getNote()));
        record.setMarkedByEmployee(resolveMarkedByEmployee(employee, requestDto.isManualEntry()));
        record.setStatus(AttendanceStatus.INCOMPLETE);

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        emailNotificationService.sendAttendanceConfirmationEmail(
                employee.getEmail(),
                buildFullName(employee),
                "Check-in recorded",
                saved.getCheckIn()
        );
        return toAttendanceResponse(saved);
    }

    @Override
    public AttendanceResponseDto checkOut(AttendanceCheckOutRequestDto requestDto) {
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());
        LocalDate workDate = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

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
    record.setLate(record.isLate() || isLateArrival(record.getCheckIn()));
    record.setManualEntry(record.isManualEntry() || requestDto.isManualEntry());
    record.setNote(mergeNotes(record.getNote(), requestDto.getNote()));
    record.setMarkedByEmployee(resolveMarkedByEmployee(employee, requestDto.isManualEntry()));
        record.setStatus(resolveAttendanceStatus(record.getCheckIn(), record.getCheckOut()));

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        emailNotificationService.sendAttendanceConfirmationEmail(
                employee.getEmail(),
                buildFullName(employee),
                "Check-out recorded",
                saved.getCheckOut()
        );
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
        long activeEmployees = employeeRepository.countByStatus(UserStatus.ACTIVE);

        long presentCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long lateCount = records.stream().filter(AttendanceRecord::isLate).count();
        long halfDayCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
        long incompleteCount = records.stream().filter(r -> r.getStatus() == AttendanceStatus.INCOMPLETE).count();
        long absentCount = Math.max(activeEmployees - records.size(), 0L);

        return AttendanceDailySummaryDto.builder()
                .workDate(workDate)
                .totalRecords(records.size())
                .presentCount(presentCount)
            .lateCount(lateCount)
                .halfDayCount(halfDayCount)
                .incompleteCount(incompleteCount)
            .absentCount(absentCount)
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
    long lateDays = records.stream().filter(AttendanceRecord::isLate).count();
        long halfDays = records.stream().filter(r -> r.getStatus() == AttendanceStatus.HALF_DAY).count();
        long incompleteDays = records.stream().filter(r -> r.getStatus() == AttendanceStatus.INCOMPLETE).count();

        return AttendanceMonthlySummaryDto.builder()
                .employeeId(employeeId)
                .year(year)
                .month(month)
                .totalDays(records.size())
                .presentDays(presentDays)
        .lateDays(lateDays)
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
        if (workedMinutes < HALF_DAY_MINUTES) {
            return AttendanceStatus.INCOMPLETE;
        }
        if (workedMinutes < FULL_DAY_MINUTES) {
            return AttendanceStatus.HALF_DAY;
        }
        return AttendanceStatus.PRESENT;
    }

    private boolean isLateArrival(LocalDateTime checkIn) {
        return checkIn.toLocalTime().isAfter(LATE_CUTOFF);
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String mergeNotes(String currentNote, String newNote) {
        String normalizedNewNote = normalizeNote(newNote);
        if (normalizedNewNote != null) {
            return normalizedNewNote;
        }
        return normalizeNote(currentNote);
    }

    private Employee resolveMarkedByEmployee(Employee targetEmployee, boolean manualEntry) {
        if (!manualEntry) {
            return targetEmployee;
        }

        Optional<Employee> currentEmployee = getCurrentEmployeeOptional();
        return currentEmployee.orElse(targetEmployee);
    }

    private Optional<Employee> getCurrentEmployeeOptional() {
        try {
            String email = securityUtils.getCurrentUserEmailOrThrow();
            return employeeRepository.findByEmailIgnoreCase(email);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private AttendanceResponseDto toAttendanceResponse(AttendanceRecord attendanceRecord) {
        Long workedMinutes = null;
        if (attendanceRecord.getCheckIn() != null && attendanceRecord.getCheckOut() != null) {
            workedMinutes = Duration.between(attendanceRecord.getCheckIn(), attendanceRecord.getCheckOut()).toMinutes();
        }

        return AttendanceResponseDto.builder()
                .id(attendanceRecord.getId())
                .employee(tenantDtoMapper.toEmployeeSimple(attendanceRecord.getEmployee()))
                .workDate(attendanceRecord.getWorkDate())
                .checkIn(attendanceRecord.getCheckIn())
                .checkOut(attendanceRecord.getCheckOut())
                .late(attendanceRecord.isLate())
                .manualEntry(attendanceRecord.isManualEntry())
                .note(attendanceRecord.getNote())
                .markedByEmployee(attendanceRecord.getMarkedByEmployee() == null ? null : tenantDtoMapper.toEmployeeSimple(attendanceRecord.getMarkedByEmployee()))
                .workedMinutes(workedMinutes)
                .status(attendanceRecord.getStatus())
                .createdAt(attendanceRecord.getCreatedAt())
                .build();
    }

    private String buildFullName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }
}
