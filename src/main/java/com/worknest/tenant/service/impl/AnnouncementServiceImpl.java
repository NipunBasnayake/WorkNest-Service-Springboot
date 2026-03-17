package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.announcement.AnnouncementUpdateRequestDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.service.AnnouncementService;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TenantRealtimePublisher tenantRealtimePublisher;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            EmployeeRepository employeeRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TenantRealtimePublisher tenantRealtimePublisher) {
        this.announcementRepository = announcementRepository;
        this.employeeRepository = employeeRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
    }

    @Override
    public AnnouncementResponseDto createAnnouncement(AnnouncementCreateRequestDto requestDto) {
        Employee creator = resolveCreatorForAnnouncement(requestDto);

        Announcement announcement = new Announcement();
        announcement.setTitle(requestDto.getTitle().trim());
        announcement.setMessage(requestDto.getMessage().trim());
        announcement.setCreatedBy(creator);

        Announcement saved = announcementRepository.save(announcement);
        AnnouncementResponseDto response = toResponse(saved);

        notifyActiveEmployees(saved);
        tenantRealtimePublisher.publishAnnouncement(securityUtils.getCurrentTenantKeyOrThrow(), response);

        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.ANNOUNCEMENT,
                saved.getId(),
                "{\"title\":\"" + escapeJson(saved.getTitle()) + "\"}"
        );

        return response;
    }

    @Override
    public AnnouncementResponseDto updateAnnouncement(Long announcementId, AnnouncementUpdateRequestDto requestDto) {
        Announcement announcement = getAnnouncementOrThrow(announcementId);
        if (!canManageAnnouncement(announcement)) {
            throw new ForbiddenOperationException("You are not allowed to update this announcement");
        }

        announcement.setTitle(requestDto.getTitle().trim());
        announcement.setMessage(requestDto.getMessage().trim());

        Announcement updated = announcementRepository.save(announcement);

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.ANNOUNCEMENT,
                updated.getId(),
                "{\"title\":\"" + escapeJson(updated.getTitle()) + "\"}"
        );

        return toResponse(updated);
    }

    @Override
    public void deleteAnnouncement(Long announcementId) {
        Announcement announcement = getAnnouncementOrThrow(announcementId);
        if (!canManageAnnouncement(announcement)) {
            throw new ForbiddenOperationException("You are not allowed to delete this announcement");
        }

        announcementRepository.delete(announcement);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.ANNOUNCEMENT,
                announcementId,
                null
        );
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AnnouncementResponseDto> listAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<AnnouncementResponseDto> listAnnouncementsPaged(
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Announcement> resultPage = announcementRepository.search(
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<AnnouncementResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AnnouncementResponseDto getAnnouncement(Long announcementId) {
        return toResponse(getAnnouncementOrThrow(announcementId));
    }

    private void notifyActiveEmployees(Announcement announcement) {
        String message = "New announcement: " + announcement.getTitle();
        List<Employee> activeEmployees = employeeRepository.findByStatus(UserStatus.ACTIVE);
        for (Employee employee : activeEmployees) {
            notificationService.createSystemNotification(
                    employee.getId(),
                    NotificationType.ANNOUNCEMENT,
                    message,
                    AuditEntityType.ANNOUNCEMENT.name(),
                    announcement.getId()
            );
        }
    }

    private Announcement getAnnouncementOrThrow(Long announcementId) {
        return announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
    }

    private Employee resolveCreatorForAnnouncement(AnnouncementCreateRequestDto requestDto) {
        String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
        Employee currentEmployee = employeeRepository.findByEmailIgnoreCase(currentEmail).orElse(null);
        if (currentEmployee != null) {
            return currentEmployee;
        }

        if (securityUtils.getCurrentRoleOrThrow() != PlatformRole.TENANT_ADMIN) {
            throw new ResourceNotFoundException("Current user does not have an employee profile");
        }

        if (requestDto.getCreatedByEmployeeId() == null) {
            throw new BadRequestException("createdByEmployeeId is required for TENANT_ADMIN users");
        }

        Employee creator = employeeRepository.findById(requestDto.getCreatedByEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + requestDto.getCreatedByEmployeeId()));

        if (creator.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Creator employee must be active");
        }
        return creator;
    }

    private boolean canManageAnnouncement(Announcement announcement) {
        PlatformRole currentRole = securityUtils.getCurrentRoleOrThrow();
        if (currentRole == PlatformRole.TENANT_ADMIN ||
                currentRole == PlatformRole.ADMIN ||
                currentRole == PlatformRole.HR) {
            return true;
        }

        String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
        return announcement.getCreatedBy().getEmail().equalsIgnoreCase(currentEmail);
    }

    private AnnouncementResponseDto toResponse(Announcement announcement) {
        return AnnouncementResponseDto.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .message(announcement.getMessage())
                .createdBy(tenantDtoMapper.toEmployeeSimple(announcement.getCreatedBy()))
                .createdAt(announcement.getCreatedAt())
                .updatedAt(announcement.getUpdatedAt())
                .build();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "title".equals(sortBy);
    }
}
