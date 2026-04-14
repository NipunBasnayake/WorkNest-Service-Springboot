package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.attachment.AttachmentCreateRequestDto;
import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.leave.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.service.AttachmentService;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.LeaveRequestService;
import com.worknest.tenant.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final AttachmentService attachmentService;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EmailNotificationService emailNotificationService;

    public LeaveRequestServiceImpl(
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService,
            AttachmentService attachmentService,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            EmailNotificationService emailNotificationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.attachmentService = attachmentService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public LeaveResponseDto applyLeave(LeaveApplyRequestDto requestDto) {
        authorizationService.requirePermission(Permission.APPLY_LEAVE);
        Employee employee = getCurrentEmployeeOrThrow();
        validateLeaveDates(requestDto.getStartDate(), requestDto.getEndDate());
        validateNoLeaveOverlap(employee.getId(), null, requestDto.getStartDate(), requestDto.getEndDate());

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(requestDto.getLeaveType());
        leaveRequest.setStartDate(requestDto.getStartDate());
        leaveRequest.setEndDate(requestDto.getEndDate());
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setReason(trimToNull(requestDto.getReason()));

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        syncAttachments(saved.getId(), requestDto.getAttachments());
        notifyApproversAboutRequest(saved);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.LEAVE_REQUEST,
                saved.getId(),
                "{\"employeeId\":" + employee.getId() + "}"
        );
        return toLeaveResponse(saved, true);
    }

    @Override
    public LeaveResponseDto updateLeave(Long leaveRequestId, LeaveUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.APPLY_LEAVE);
        LeaveRequest leaveRequest = getLeaveRequestOrThrow(leaveRequestId);
        Employee currentEmployee = getCurrentEmployeeOrThrow();

        if (!leaveRequest.getEmployee().getId().equals(currentEmployee.getId())) {
            throw new BadRequestException("You can update only your own leave requests");
        }

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leave requests can be updated");
        }

        validateLeaveDates(requestDto.getStartDate(), requestDto.getEndDate());
        validateNoLeaveOverlap(currentEmployee.getId(), leaveRequest.getId(), requestDto.getStartDate(), requestDto.getEndDate());

        leaveRequest.setLeaveType(requestDto.getLeaveType());
        leaveRequest.setStartDate(requestDto.getStartDate());
        leaveRequest.setEndDate(requestDto.getEndDate());
        leaveRequest.setReason(trimToNull(requestDto.getReason()));

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        syncAttachments(updated.getId(), requestDto.getAttachments());
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.LEAVE_REQUEST,
                updated.getId(),
                null
        );
        return toLeaveResponse(updated, true);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<LeaveResponseDto> listMyLeaveRequests() {
        authorizationService.requirePermission(Permission.VIEW_LEAVE);
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(currentEmployee.getId()).stream()
            .map(leaveRequest -> toLeaveResponse(leaveRequest, false))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<LeaveResponseDto> listPendingLeaveRequests() {
        authorizationService.requirePermission(Permission.APPROVE_LEAVE);
        return leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING).stream()
            .map(leaveRequest -> toLeaveResponse(leaveRequest, false))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<LeaveResponseDto> listMyLeaveRequestsPaged(
            LeaveStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.VIEW_LEAVE);
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        validateDateRange(fromDate, toDate);

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<LeaveRequest> resultPage = leaveRequestRepository.searchMyRequests(
                currentEmployee.getId(),
                status,
                fromDate,
                toDate,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<LeaveResponseDto>builder()
            .items(resultPage.getContent().stream().map(leaveRequest -> toLeaveResponse(leaveRequest, false)).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<LeaveResponseDto> listLeaveRequestsPaged(
            LeaveStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.APPROVE_LEAVE);
        validateDateRange(fromDate, toDate);

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<LeaveRequest> resultPage = leaveRequestRepository.search(
                status,
                fromDate,
                toDate,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<LeaveResponseDto>builder()
            .items(resultPage.getContent().stream().map(leaveRequest -> toLeaveResponse(leaveRequest, false)).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    public LeaveResponseDto approveLeave(Long leaveRequestId, LeaveDecisionRequestDto requestDto) {
        authorizationService.requirePermission(Permission.APPROVE_LEAVE);
        LeaveRequest leaveRequest = getLeaveRequestOrThrow(leaveRequestId);
        Employee approver = getCurrentEmployeeOrThrow();
        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Approver account is not active");
        }
        validateApproverForLeave(leaveRequest, approver);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leave requests can be approved");
        }

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setApprover(approver);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setDecisionComment(trimToNull(requestDto.getDecisionComment()));

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        notificationService.createSystemNotification(
                leaveRequest.getEmployee().getId(),
                NotificationType.LEAVE_APPROVED,
                "Your leave request has been approved",
                AuditEntityType.LEAVE_REQUEST.name(),
                updated.getId()
        );
        emailNotificationService.sendLeaveApprovedEmail(
                leaveRequest.getEmployee().getEmail(),
                buildFullName(leaveRequest.getEmployee()),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                buildFullName(approver)
        );
        auditLogService.logAction(
                AuditActionType.APPROVE,
                AuditEntityType.LEAVE_REQUEST,
                updated.getId(),
                "{\"approverId\":" + approver.getId() + "}"
        );
        return toLeaveResponse(updated, true);
    }

    @Override
    public LeaveResponseDto rejectLeave(Long leaveRequestId, LeaveDecisionRequestDto requestDto) {
        authorizationService.requirePermission(Permission.APPROVE_LEAVE);
        LeaveRequest leaveRequest = getLeaveRequestOrThrow(leaveRequestId);
        Employee approver = getCurrentEmployeeOrThrow();
        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Approver account is not active");
        }
        validateApproverForLeave(leaveRequest, approver);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leave requests can be rejected");
        }

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setApprover(approver);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setDecisionComment(trimToNull(requestDto.getDecisionComment()));

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        notificationService.createSystemNotification(
                leaveRequest.getEmployee().getId(),
                NotificationType.LEAVE_REJECTED,
                "Your leave request has been rejected",
                AuditEntityType.LEAVE_REQUEST.name(),
                updated.getId()
        );
        emailNotificationService.sendLeaveRejectedEmail(
                leaveRequest.getEmployee().getEmail(),
                buildFullName(leaveRequest.getEmployee()),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                buildFullName(approver),
                requestDto.getDecisionComment()
        );
        auditLogService.logAction(
                AuditActionType.REJECT,
                AuditEntityType.LEAVE_REQUEST,
                updated.getId(),
                "{\"approverId\":" + approver.getId() + "}"
        );
        return toLeaveResponse(updated, true);
    }

    @Override
    public LeaveResponseDto cancelLeave(Long leaveRequestId) {
        authorizationService.requirePermission(Permission.APPLY_LEAVE);
        LeaveRequest leaveRequest = getLeaveRequestOrThrow(leaveRequestId);
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        if (!leaveRequest.getEmployee().getId().equals(currentEmployee.getId())) {
            throw new ForbiddenOperationException("You can cancel only your own leave requests");
        }
        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending leave requests can be cancelled");
        }

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);

        notificationService.createSystemNotification(
            currentEmployee.getId(),
            NotificationType.LEAVE_UPDATE,
            "You cancelled your leave request",
            AuditEntityType.LEAVE_REQUEST.name(),
            updated.getId()
        );
        emailNotificationService.sendLeaveCancelledConfirmationEmail(
            currentEmployee.getEmail(),
            buildFullName(currentEmployee),
            leaveRequest.getStartDate(),
            leaveRequest.getEndDate()
        );
        notifyApproversAboutCancellation(currentEmployee, updated);
        auditLogService.logAction(
                AuditActionType.CANCEL,
                AuditEntityType.LEAVE_REQUEST,
                updated.getId(),
                "{\"employeeId\":" + currentEmployee.getId() + "}"
        );

        return toLeaveResponse(updated, true);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public LeaveResponseDto getLeaveDetails(Long leaveRequestId) {
        authorizationService.requirePermission(Permission.VIEW_LEAVE);
        LeaveRequest leaveRequest = getLeaveRequestOrThrow(leaveRequestId);
        PlatformRole currentRole = authorizationService.getCurrentRoleOrThrow();
        if (currentRole == PlatformRole.EMPLOYEE) {
            Employee currentEmployee = getCurrentEmployeeOrThrow();
            if (!leaveRequest.getEmployee().getId().equals(currentEmployee.getId())) {
                throw new ForbiddenOperationException("Employees can only view their own leave requests");
            }
        }
        return toLeaveResponse(leaveRequest, true);
    }

    private LeaveRequest getLeaveRequestOrThrow(Long leaveRequestId) {
        return leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + leaveRequestId));
    }

    private Employee getCurrentEmployeeOrThrow() {
        return authorizationService.getCurrentEmployeeOrThrow();
    }

    private void validateLeaveDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Leave start and end dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("Leave end date cannot be before start date");
        }
        if (endDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Leave request cannot be fully in the past");
        }
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }
    }

    private void validateNoLeaveOverlap(Long employeeId, Long excludedLeaveRequestId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        boolean hasOverlap = leaveRequestRepository.existsOverlappingLeave(
                employeeId,
                List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING),
                excludedLeaveRequestId,
                endDate,
                startDate
        );
        if (hasOverlap) {
            throw new BadRequestException("Leave request overlaps with an existing pending or approved leave");
        }
    }

    private LeaveResponseDto toLeaveResponse(LeaveRequest leaveRequest, boolean includeAttachments) {
        List<AttachmentResponseDto> attachments = includeAttachments
                ? attachmentService.listAttachments(AttachmentEntityType.LEAVE_REQUEST, leaveRequest.getId())
                : List.of();
        return LeaveResponseDto.builder()
                .id(leaveRequest.getId())
                .employee(tenantDtoMapper.toEmployeeSimple(leaveRequest.getEmployee()))
                .decidedBy(tenantDtoMapper.toEmployeeSimple(leaveRequest.getApprover()))
                .leaveType(leaveRequest.getLeaveType())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .status(leaveRequest.getStatus())
                .approver(tenantDtoMapper.toEmployeeSimple(leaveRequest.getApprover()))
                .reason(leaveRequest.getReason())
                .decisionComment(leaveRequest.getDecisionComment())
                .decidedAt(leaveRequest.getDecidedAt())
                .attachments(attachments)
                .createdAt(leaveRequest.getCreatedAt())
                .updatedAt(leaveRequest.getUpdatedAt())
                .build();
    }


    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateApproverForLeave(LeaveRequest leaveRequest, Employee approver) {
        if (leaveRequest.getEmployee().getId().equals(approver.getId())) {
            throw new BadRequestException("You cannot approve your own leave request");
        }

        PlatformRole requesterRole = leaveRequest.getEmployee().getRole();
        PlatformRole approverRole = approver.getRole();

        if (requesterRole == null || approverRole == null) {
            throw new BadRequestException("Leave request roles are not configured correctly");
        }

        if (requesterRole.isHrEquivalent()) {
            if (!approverRole.isTenantAdminEquivalent()) {
                throw new BadRequestException("HR leave requests can only be approved by TENANT_ADMIN or ADMIN");
            }
            return;
        }

        if (requesterRole.isTenantAdminEquivalent() || requesterRole.isEmployeeOnly() || requesterRole.isLegacyManager()) {
            if (!(approverRole.isTenantAdminEquivalent() || approverRole.isHrEquivalent())) {
                throw new BadRequestException("Leave requests can only be approved by HR or TENANT_ADMIN/ADMIN");
            }

            if ((requesterRole.isTenantAdminEquivalent() || requesterRole.isLegacyManager()) && !approverRole.isTenantAdminEquivalent()) {
                throw new BadRequestException("TENANT_ADMIN leave requests can only be approved by TENANT_ADMIN or ADMIN");
            }
            return;
        }

        throw new BadRequestException("Leave request cannot be approved for this role");
    }

    private void syncAttachments(Long leaveRequestId, List<LeaveAttachmentRequestDto> attachments) {
        if (attachments == null) {
            return;
        }

        List<AttachmentResponseDto> existingAttachments = attachmentService.listAttachments(AttachmentEntityType.LEAVE_REQUEST, leaveRequestId);
        for (AttachmentResponseDto attachment : existingAttachments) {
            attachmentService.deleteAttachment(attachment.getId());
        }

        for (LeaveAttachmentRequestDto attachment : attachments) {
            if (attachment == null) {
                continue;
            }

            AttachmentCreateRequestDto createRequest = new AttachmentCreateRequestDto();
            createRequest.setEntityType(AttachmentEntityType.LEAVE_REQUEST);
            createRequest.setEntityId(leaveRequestId);
            createRequest.setFileUrl(attachment.getFileUrl());
            createRequest.setFileName(attachment.getFileName());
            createRequest.setFileType(attachment.getFileType());
            createRequest.setFileSize(attachment.getFileSize());
            attachmentService.createAttachment(createRequest);
        }
    }

    private void notifyApproversAboutRequest(LeaveRequest leaveRequest) {
        List<PlatformRole> roles = resolveApproverRecipientRoles(leaveRequest.getEmployee().getRole());
        if (roles.isEmpty()) {
            return;
        }

        List<Employee> recipients = employeeRepository.findByRoleInAndStatus(roles, UserStatus.ACTIVE).stream()
                .filter(candidate -> !candidate.getId().equals(leaveRequest.getEmployee().getId()))
                .toList();

        String requesterName = buildFullName(leaveRequest.getEmployee());
        String message = requesterName + " submitted a leave request for " + leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate();
        for (Employee recipient : recipients) {
            notificationService.createSystemNotification(
                    recipient.getId(),
                    NotificationType.LEAVE_UPDATE,
                    message,
                    AuditEntityType.LEAVE_REQUEST.name(),
                    leaveRequest.getId()
            );
            emailNotificationService.sendLeaveRequestSubmittedEmail(
                    recipient.getEmail(),
                    buildFullName(recipient),
                    requesterName,
                    leaveRequest.getLeaveType() == null ? "leave" : leaveRequest.getLeaveType().name(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(),
                    leaveRequest.getReason()
            );
        }
    }

    private List<PlatformRole> resolveApproverRecipientRoles(PlatformRole requesterRole) {
        if (requesterRole == null) {
            return List.of();
        }

        if (requesterRole.isHrEquivalent()) {
            return List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN);
        }

        if (requesterRole.isTenantAdminEquivalent() || requesterRole.isLegacyManager()) {
            return List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN);
        }

        return List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN, PlatformRole.HR);
    }

    private void notifyApproversAboutCancellation(Employee employee, LeaveRequest leaveRequest) {
        List<Employee> recipients = employeeRepository.findByRoleInAndStatus(
                List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN, PlatformRole.HR),
                UserStatus.ACTIVE
        ).stream()
                .filter(candidate -> !candidate.getId().equals(employee.getId()))
                .toList();

        String message = "Leave request cancelled by " + employee.getFirstName() + " " + employee.getLastName();
        for (Employee recipient : recipients) {
            notificationService.createSystemNotification(
                    recipient.getId(),
                    NotificationType.LEAVE_CANCELLED,
                    message,
                    AuditEntityType.LEAVE_REQUEST.name(),
                    leaveRequest.getId()
            );
            emailNotificationService.sendLeaveCancelledAlertEmail(
                    recipient.getEmail(),
                    buildFullName(recipient),
                    buildFullName(employee),
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate()
            );
        }
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) ||
                "updatedAt".equals(sortBy) ||
                "startDate".equals(sortBy) ||
                "endDate".equals(sortBy) ||
                "status".equals(sortBy);
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
