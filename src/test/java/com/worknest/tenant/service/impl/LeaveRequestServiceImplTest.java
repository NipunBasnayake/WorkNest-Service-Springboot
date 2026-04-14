package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.leave.LeaveApplyRequestDto;
import com.worknest.tenant.dto.leave.LeaveDecisionRequestDto;
import com.worknest.tenant.dto.leave.LeaveUpdateRequestDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.LeaveType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.service.AttachmentService;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceImplTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private EmailNotificationService emailNotificationService;

    private TenantDtoMapper tenantDtoMapper;
    private LeaveRequestServiceImpl leaveRequestService;

    @BeforeEach
    void setUp() {
        tenantDtoMapper = new TenantDtoMapper();
        leaveRequestService = new LeaveRequestServiceImpl(
                leaveRequestRepository,
                employeeRepository,
                authorizationService,
                attachmentService,
                tenantDtoMapper,
                notificationService,
                auditLogService,
                emailNotificationService
        );

        doNothing().when(authorizationService).requirePermission(any(Permission.class));
        when(authorizationService.getCurrentEmployeeOrThrow()).thenAnswer(invocation -> null);
        when(attachmentService.listAttachments(any(), anyLong())).thenReturn(List.of());
        when(employeeRepository.findByRoleInAndStatus(anyList(), any())).thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void employeeCanRequestLeave() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(employee);
        when(leaveRequestRepository.existsOverlappingLeave(anyLong(), anyList(), any(), any(), any())).thenReturn(false);

        LeaveApplyRequestDto request = new LeaveApplyRequestDto();
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartDate(LocalDate.of(2026, 4, 20));
        request.setEndDate(LocalDate.of(2026, 4, 21));
        request.setReason("Vacation trip");

        LeaveRequest response = captureCreate(request);

        Assertions.assertThat(response.getEmployee().getId()).isEqualTo(employee.getId());
        Assertions.assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void hrCanRequestLeave() {
        Employee hr = employee(11L, PlatformRole.HR);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(hr);
        when(leaveRequestRepository.existsOverlappingLeave(anyLong(), anyList(), any(), any(), any())).thenReturn(false);

        LeaveApplyRequestDto request = new LeaveApplyRequestDto();
        request.setLeaveType(LeaveType.SICK);
        request.setStartDate(LocalDate.of(2026, 4, 20));
        request.setEndDate(LocalDate.of(2026, 4, 20));
        request.setReason("Medical appointment");

        LeaveRequest response = captureCreate(request);

        Assertions.assertThat(response.getEmployee().getId()).isEqualTo(hr.getId());
        Assertions.assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void employeeLeaveCanBeApprovedByHr() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        Employee approver = employee(20L, PlatformRole.HR);
        LeaveRequest pending = leaveRequest(1L, employee, LeaveStatus.PENDING);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(approver);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(pending));

        LeaveDecisionRequestDto request = new LeaveDecisionRequestDto();
        request.setDecisionComment("Approved");

        LeaveRequest response = leaveRequestService.approveLeave(1L, request);

        Assertions.assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        Assertions.assertThat(response.getApprover().getId()).isEqualTo(approver.getId());
        Assertions.assertThat(response.getDecidedBy().getId()).isEqualTo(approver.getId());
        Assertions.assertThat(response.getDecidedAt()).isNotNull();
    }

    @Test
    void employeeLeaveCanBeApprovedByTenantAdmin() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        Employee approver = employee(21L, PlatformRole.TENANT_ADMIN);
        LeaveRequest pending = leaveRequest(2L, employee, LeaveStatus.PENDING);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(approver);
        when(leaveRequestRepository.findById(2L)).thenReturn(Optional.of(pending));

        LeaveDecisionRequestDto request = new LeaveDecisionRequestDto();
        request.setDecisionComment("Approved");

        LeaveRequest response = leaveRequestService.approveLeave(2L, request);

        Assertions.assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        Assertions.assertThat(response.getApprover().getId()).isEqualTo(approver.getId());
    }

    @Test
    void hrLeaveCanBeRejectedByTenantAdmin() {
        Employee requester = employee(10L, PlatformRole.HR);
        Employee approver = employee(21L, PlatformRole.TENANT_ADMIN);
        LeaveRequest pending = leaveRequest(3L, requester, LeaveStatus.PENDING);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(approver);
        when(leaveRequestRepository.findById(3L)).thenReturn(Optional.of(pending));

        LeaveDecisionRequestDto request = new LeaveDecisionRequestDto();
        request.setDecisionComment("Coverage unavailable");

        LeaveRequest response = leaveRequestService.rejectLeave(3L, request);

        Assertions.assertThat(response.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        Assertions.assertThat(response.getApprover().getId()).isEqualTo(approver.getId());
        Assertions.assertThat(response.getDecisionComment()).isEqualTo("Coverage unavailable");
    }

    @Test
    void hrCannotApproveHrLeave() {
        Employee requester = employee(10L, PlatformRole.HR);
        Employee approver = employee(20L, PlatformRole.HR);
        LeaveRequest pending = leaveRequest(4L, requester, LeaveStatus.PENDING);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(approver);
        when(leaveRequestRepository.findById(4L)).thenReturn(Optional.of(pending));

        LeaveDecisionRequestDto request = new LeaveDecisionRequestDto();
        request.setDecisionComment("Approved");

        Assertions.assertThatThrownBy(() -> leaveRequestService.approveLeave(4L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("HR leave requests can only be approved");
    }

    @Test
    void requesterCannotApproveOwnLeave() {
        Employee requester = employee(10L, PlatformRole.EMPLOYEE);
        LeaveRequest pending = leaveRequest(5L, requester, LeaveStatus.PENDING);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(requester);
        when(leaveRequestRepository.findById(5L)).thenReturn(Optional.of(pending));

        LeaveDecisionRequestDto request = new LeaveDecisionRequestDto();
        request.setDecisionComment("Approved");

        Assertions.assertThatThrownBy(() -> leaveRequestService.approveLeave(5L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot approve your own leave request");
    }

    @Test
    void overlapIsBlockedWhenRequestingLeave() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(employee);
        when(leaveRequestRepository.existsOverlappingLeave(anyLong(), anyList(), any(), any(), any())).thenReturn(true);

        LeaveApplyRequestDto request = new LeaveApplyRequestDto();
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartDate(LocalDate.of(2026, 4, 20));
        request.setEndDate(LocalDate.of(2026, 4, 21));
        request.setReason("Vacation trip");

        Assertions.assertThatThrownBy(() -> leaveRequestService.applyLeave(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlaps with an existing pending or approved leave");
    }

    @Test
    void editOnlyWhilePending() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        LeaveRequest approved = leaveRequest(6L, employee, LeaveStatus.APPROVED);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(employee);
        when(leaveRequestRepository.findById(6L)).thenReturn(Optional.of(approved));

        LeaveUpdateRequestDto request = new LeaveUpdateRequestDto();
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStartDate(LocalDate.of(2026, 4, 22));
        request.setEndDate(LocalDate.of(2026, 4, 23));
        request.setReason("Updated reason");

        Assertions.assertThatThrownBy(() -> leaveRequestService.updateLeave(6L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending leave requests can be updated");
    }

    @Test
    void cancelOnlyWhilePending() {
        Employee employee = employee(10L, PlatformRole.EMPLOYEE);
        LeaveRequest approved = leaveRequest(7L, employee, LeaveStatus.APPROVED);
        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(employee);
        when(leaveRequestRepository.findById(7L)).thenReturn(Optional.of(approved));

        Assertions.assertThatThrownBy(() -> leaveRequestService.cancelLeave(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending leave requests can be cancelled");
    }

    private LeaveRequest captureCreate(LeaveApplyRequestDto request) {
        leaveRequestService.applyLeave(request);
        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    private Employee employee(Long id, PlatformRole role) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("EMP-" + id);
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmail(role.name().toLowerCase() + "@worknest.test");
        employee.setRole(role);
        employee.setStatus(UserStatus.ACTIVE);
        return employee;
    }

    private LeaveRequest leaveRequest(Long id, Employee requester, LeaveStatus status) {
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setId(id);
        leaveRequest.setEmployee(requester);
        leaveRequest.setLeaveType(LeaveType.ANNUAL);
        leaveRequest.setStartDate(LocalDate.of(2026, 4, 20));
        leaveRequest.setEndDate(LocalDate.of(2026, 4, 21));
        leaveRequest.setStatus(status);
        leaveRequest.setReason("Test leave");
        leaveRequest.setCreatedAt(LocalDateTime.of(2026, 4, 14, 8, 0));
        leaveRequest.setUpdatedAt(LocalDateTime.of(2026, 4, 14, 8, 0));
        return leaveRequest;
    }
}
