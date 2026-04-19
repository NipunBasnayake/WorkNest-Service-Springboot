package com.worknest.tenant.service.impl;

import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.notification.NotificationCreateRequestDto;
import com.worknest.tenant.dto.notification.NotificationResponseDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Notification;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.NotificationRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(transactionManager = "transactionManager")
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final TenantRealtimePublisher tenantRealtimePublisher;
    private final AuditLogService auditLogService;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            TenantRealtimePublisher tenantRealtimePublisher,
            AuditLogService auditLogService) {
        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
        this.auditLogService = auditLogService;
    }

    @Override
    public NotificationResponseDto createNotification(NotificationCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.SEND_NOTIFICATION);
        String referenceType = resolveReferenceType(requestDto);
        Long referenceId = resolveReferenceId(requestDto);

        NotificationResponseDto response = createSystemNotification(
                requestDto.getRecipientEmployeeId(),
                requestDto.getType(),
                requestDto.getMessage(),
            referenceType,
            referenceId
        );

        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.NOTIFICATION,
                response.getId(),
                "{\"type\":\"" + response.getType() + "\"}"
        );

        return response;
    }

    @Override
    public NotificationResponseDto createSystemNotification(
            Long recipientEmployeeId,
            NotificationType type,
            String message,
            String referenceType,
            Long referenceId) {

        Employee recipient = getEmployeeOrThrow(recipientEmployeeId);
        if (type == null) {
            throw new BadRequestException("Notification type is required");
        }
        String cleanMessage = trimToNull(message);
        if (cleanMessage == null) {
            throw new BadRequestException("Notification message is required");
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(cleanMessage);
        notification.setReferenceType(normalizeReferenceType(referenceType));
        notification.setReferenceId(referenceId);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        NotificationResponseDto response = toResponse(saved);

        tenantRealtimePublisher.publishNotification(recipient.getEmail(), response);
        return response;
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<NotificationResponseDto> listMyNotifications() {
        Employee me = getCurrentEmployeeOrThrow();
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<NotificationResponseDto> listMyNotificationsPaged(
            Boolean readFilter,
            NotificationType type,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        Employee me = getCurrentEmployeeOrThrow();

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Notification> resultPage = notificationRepository.searchMyNotifications(
                me.getId(),
                readFilter,
                type,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<NotificationResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    public NotificationResponseDto markAsRead(Long notificationId) {
        Employee me = getCurrentEmployeeOrThrow();
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, me.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        }

        Notification saved = notificationRepository.save(notification);
        auditLogService.logAction(
                AuditActionType.MARK_READ,
                AuditEntityType.NOTIFICATION,
                saved.getId(),
                "{\"recipientId\":" + me.getId() + "}"
        );

        return toResponse(saved);
    }

    @Override
    public long markAllAsRead() {
        Employee me = getCurrentEmployeeOrThrow();
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.getId());

        long updatedCount = 0L;
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
                notification.setReadAt(now);
                updatedCount++;
            }
        }

        notificationRepository.saveAll(notifications);
        if (updatedCount > 0) {
            auditLogService.logAction(
                    AuditActionType.MARK_READ,
                    AuditEntityType.NOTIFICATION,
                    null,
                    "{\"recipientId\":" + me.getId() + ",\"count\":" + updatedCount + "}"
            );
        }
        return updatedCount;
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public long getMyUnreadCount() {
        Employee me = getCurrentEmployeeOrThrow();
        return notificationRepository.countByRecipientIdAndReadFalse(me.getId());
    }

    private Employee getCurrentEmployeeOrThrow() {
        return authorizationService.getCurrentEmployeeOrThrow();
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    private NotificationResponseDto toResponse(Notification notification) {
        Long announcementId = resolveAnnouncementId(notification);
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .recipient(tenantDtoMapper.toEmployeeSimple(notification.getRecipient()))
                .type(notification.getType())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .relatedEntityType(notification.getReferenceType())
                .relatedEntityId(notification.getReferenceId())
                .announcementId(announcementId)
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    private String resolveReferenceType(NotificationCreateRequestDto requestDto) {
        String fromAnnouncement = requestDto.getAnnouncementId() == null ? null : AuditEntityType.ANNOUNCEMENT.name();
        return normalizeReferenceType(firstNonBlank(
                requestDto.getReferenceType(),
                requestDto.getRelatedEntityType(),
                fromAnnouncement
        ));
    }

    private Long resolveReferenceId(NotificationCreateRequestDto requestDto) {
        return firstNonNull(
                requestDto.getReferenceId(),
                requestDto.getRelatedEntityId(),
                requestDto.getAnnouncementId()
        );
    }

    private Long resolveAnnouncementId(Notification notification) {
        Long referenceId = notification.getReferenceId();
        if (referenceId == null) {
            return null;
        }

        String referenceType = trimToNull(notification.getReferenceType());
        if (AuditEntityType.ANNOUNCEMENT.name().equalsIgnoreCase(referenceType)
                || notification.getType() == NotificationType.ANNOUNCEMENT) {
            return referenceId;
        }
        return null;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String trimmed = trimToNull(candidate);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... candidates) {
        if (candidates == null) {
            return null;
        }
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeReferenceType(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "readAt".equals(sortBy) || "type".equals(sortBy);
    }
}
