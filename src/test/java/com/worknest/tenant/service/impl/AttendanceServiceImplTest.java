package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.attendance.AttendanceCheckInRequestDto;
import com.worknest.tenant.dto.attendance.AttendanceCheckOutRequestDto;
import com.worknest.tenant.dto.attendance.AttendanceResponseDto;
import com.worknest.tenant.entity.AttendanceRecord;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AttendanceStatus;
import com.worknest.tenant.repository.AttendanceRecordRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private SecurityUtils securityUtils;

    private TenantDtoMapper tenantDtoMapper;
    private MutableClock clock;
    private AttendanceServiceImpl attendanceService;

    @BeforeEach
    void setUp() {
        tenantDtoMapper = new TenantDtoMapper();
        clock = new MutableClock(Instant.parse("2026-04-14T09:30:00Z"), ZoneOffset.UTC);
        attendanceService = new AttendanceServiceImpl(
                attendanceRecordRepository,
                employeeRepository,
                tenantDtoMapper,
                emailNotificationService,
                securityUtils,
                clock
        );
    }

    @Test
    void checkInMarksLateAndManualEntries() {
        Employee employee = employee(1L, "employee@worknest.test", UserStatus.ACTIVE);
        Employee hr = employee(9L, "hr@worknest.test", UserStatus.ACTIVE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmailIgnoreCase("hr@worknest.test")).thenReturn(Optional.of(hr));
        when(securityUtils.getCurrentUserEmailOrThrow()).thenReturn("hr@worknest.test");
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, LocalDate.of(2026, 4, 14)))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailNotificationService).sendAttendanceConfirmationEmail(any(), any(), any(), any());

        AttendanceCheckInRequestDto request = new AttendanceCheckInRequestDto();
        request.setEmployeeId(1L);
        request.setManualEntry(true);
        request.setNote("Front desk override");

        AttendanceResponseDto response = attendanceService.checkIn(request);

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).save(captor.capture());

        AttendanceRecord saved = captor.getValue();
        Assertions.assertThat(saved.isLate()).isTrue();
        Assertions.assertThat(saved.isManualEntry()).isTrue();
        Assertions.assertThat(saved.getMarkedByEmployee()).isSameAs(hr);
        Assertions.assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
        Assertions.assertThat(saved.getNote()).isEqualTo("Front desk override");
        Assertions.assertThat(response.getLate()).isTrue();
        Assertions.assertThat(response.getManualEntry()).isTrue();
        Assertions.assertThat(response.getMarkedByEmployee().getId()).isEqualTo(hr.getId());
    }

    @Test
    void checkOutPromotesToHalfDayWhenWorkedTimeIsUnderFullDayThreshold() {
        Employee employee = employee(1L, "employee@worknest.test", UserStatus.ACTIVE);
        Employee hr = employee(9L, "hr@worknest.test", UserStatus.ACTIVE);

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(LocalDate.of(2026, 4, 14));
        record.setCheckIn(LocalDateTime.of(2026, 4, 14, 8, 0));
        record.setStatus(AttendanceStatus.INCOMPLETE);
        record.setManualEntry(true);
        record.setNote("Initial note");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmailIgnoreCase("hr@worknest.test")).thenReturn(Optional.of(hr));
        when(securityUtils.getCurrentUserEmailOrThrow()).thenReturn("hr@worknest.test");
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, LocalDate.of(2026, 4, 14)))
                .thenReturn(Optional.of(record));
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailNotificationService).sendAttendanceConfirmationEmail(any(), any(), any(), any());

        clock.setInstant(Instant.parse("2026-04-14T13:00:00Z"));

        AttendanceCheckOutRequestDto request = new AttendanceCheckOutRequestDto();
        request.setEmployeeId(1L);
        request.setManualEntry(true);
        request.setNote("Updated note");

        AttendanceResponseDto response = attendanceService.checkOut(request);

        Assertions.assertThat(response.getStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        Assertions.assertThat(response.getWorkedMinutes()).isEqualTo(300L);
        Assertions.assertThat(response.getManualEntry()).isTrue();
        Assertions.assertThat(response.getNote()).isEqualTo("Updated note");
        Assertions.assertThat(response.getMarkedByEmployee().getId()).isEqualTo(hr.getId());
    }

    @Test
    void checkOutUnderFourHoursStaysIncomplete() {
        Employee employee = employee(1L, "employee@worknest.test", UserStatus.ACTIVE);

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(LocalDate.of(2026, 4, 14));
        record.setCheckIn(LocalDateTime.of(2026, 4, 14, 8, 0));
        record.setStatus(AttendanceStatus.INCOMPLETE);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDate(1L, LocalDate.of(2026, 4, 14)))
                .thenReturn(Optional.of(record));
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailNotificationService).sendAttendanceConfirmationEmail(any(), any(), any(), any());

        clock.setInstant(Instant.parse("2026-04-14T10:30:00Z"));

        AttendanceCheckOutRequestDto request = new AttendanceCheckOutRequestDto();
        request.setEmployeeId(1L);

        AttendanceResponseDto response = attendanceService.checkOut(request);

        Assertions.assertThat(response.getStatus()).isEqualTo(AttendanceStatus.INCOMPLETE);
        Assertions.assertThat(response.getWorkedMinutes()).isEqualTo(150L);
    }

    private Employee employee(Long id, String email, UserStatus status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("EMP-" + id);
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmail(email);
        employee.setStatus(status);
        return employee;
    }

    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private Instant instant;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}