package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.context.TenantContext;
import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.announcement.AnnouncementUpdateRequestDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.AnnouncementCreatorRole;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AnnouncementService;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Transactional(transactionManager = "transactionManager")
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementServiceImpl.class);

    private final AnnouncementRepository announcementRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TenantRealtimePublisher tenantRealtimePublisher;
    private final EmailNotificationService emailNotificationService;
    private final SecurityUtils securityUtils;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TenantRealtimePublisher tenantRealtimePublisher,
            EmailNotificationService emailNotificationService,
            SecurityUtils securityUtils) {
        this.announcementRepository = announcementRepository;
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
        this.emailNotificationService = emailNotificationService;
        this.securityUtils = securityUtils;
    }

    @Override
    public AnnouncementResponseDto createAnnouncement(AnnouncementCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.SEND_ANNOUNCEMENT);
        ensureTenantAnnouncementPublisher();
        Employee creator = resolveCreatorForAnnouncement();
        Team targetTeam = resolveTeamOrNull(requestDto.getTeamId());
        AnnouncementAccessContext accessContext = resolveAnnouncementAccessContext();
        String content = resolveContentOrThrow(requestDto.resolveContent());

        Announcement announcement = new Announcement();
        announcement.setTitle(requestDto.getTitle().trim());
        announcement.setContent(content);
        announcement.setCreatedBy(creator);
        announcement.setCreatedByName(buildFullName(creator));
        announcement.setCreatedByRole(resolveCreatedByRole());
        announcement.setPinned(requestDto.isPinned());
        announcement.setTeam(targetTeam);

        Announcement saved = announcementRepository.save(announcement);
        AnnouncementResponseDto response = toResponse(saved, accessContext);

        notifyRecipients(saved);
        tenantRealtimePublisher.publishAnnouncement(authorizationService.getCurrentTenantKeyOrThrow(), response);

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
        authorizationService.requirePermission(Permission.SEND_ANNOUNCEMENT);
        ensureTenantAnnouncementPublisher();
        Announcement announcement = getAnnouncementOrThrow(announcementId);
        AnnouncementAccessContext accessContext = resolveAnnouncementAccessContext();
        if (!canManageAnnouncement(announcement, accessContext)) {
            throw new ForbiddenOperationException("You are not allowed to update this announcement");
        }

        announcement.setTitle(requestDto.getTitle().trim());
        announcement.setContent(resolveContentOrThrow(requestDto.resolveContent()));
        announcement.setPinned(requestDto.isPinned());

        Announcement updated = announcementRepository.save(announcement);

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.ANNOUNCEMENT,
                updated.getId(),
                "{\"title\":\"" + escapeJson(updated.getTitle()) + "\"}"
        );

        return toResponse(updated, accessContext);
    }

    @Override
    public void deleteAnnouncement(Long announcementId) {
        authorizationService.requirePermission(Permission.SEND_ANNOUNCEMENT);
        ensureTenantAnnouncementPublisher();
        Announcement announcement = getAnnouncementOrThrow(announcementId);
        if (!canManageAnnouncement(announcement, resolveAnnouncementAccessContext())) {
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
        authorizationService.requirePermission(Permission.VIEW_SELF_DATA);
        AnnouncementAccessContext accessContext = resolveAnnouncementAccessContext();
        List<Announcement> announcements = accessContext.privileged()
                ? announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc()
                : announcementRepository.findByTeamIsNullOrderByPinnedDescCreatedAtDesc();
        log.info("Announcement list requested by user={}, role={}, tenant={}, count={}",
                currentUserForLog(),
                authorizationService.getCurrentRoleOrThrow(),
                TenantContext.getTenantId(),
                announcements.size());
        return announcements.stream()
                .sorted(Comparator.comparing(Announcement::isPinned).reversed()
                        .thenComparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(announcement -> toResponse(announcement, accessContext))
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
        authorizationService.requirePermission(Permission.VIEW_SELF_DATA);

        AnnouncementAccessContext accessContext = resolveAnnouncementAccessContext();
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Announcement> resultPage = announcementRepository.searchVisible(
                accessContext.employeeId(),
                accessContext.privileged(),
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<AnnouncementResponseDto>builder()
                .items(resultPage.getContent().stream().map(announcement -> toResponse(announcement, accessContext)).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AnnouncementResponseDto getAnnouncement(Long announcementId) {
        authorizationService.requirePermission(Permission.VIEW_SELF_DATA);
        Announcement announcement = getAnnouncementOrThrow(announcementId);
        AnnouncementAccessContext accessContext = resolveAnnouncementAccessContext();
        enforceAnnouncementReadAccess(announcement, accessContext);
        return toResponse(announcement, accessContext);
    }

    private void notifyRecipients(Announcement announcement) {
        Set<Employee> recipients = resolveRecipients(announcement);
        boolean teamAnnouncement = announcement.getTeam() != null;

        for (Employee recipient : recipients) {
            if (announcement.getCreatedBy() != null
                    && announcement.getCreatedBy().getId() != null
                    && announcement.getCreatedBy().getId().equals(recipient.getId())) {
                continue;
            }
            notificationService.createSystemNotification(
                    recipient.getId(),
                    NotificationType.ANNOUNCEMENT,
                    "New announcement: " + announcement.getTitle(),
                    AuditEntityType.ANNOUNCEMENT.name(),
                    announcement.getId()
            );

            if (teamAnnouncement) {
                emailNotificationService.sendTeamAnnouncementEmail(
                        recipient.getEmail(),
                        buildFullName(recipient),
                        announcement.getTeam().getName(),
                        announcement.getTitle(),
                        announcement.getContent(),
                        buildFullName(announcement.getCreatedBy())
                );
            } else {
                emailNotificationService.sendCompanyAnnouncementEmail(
                        recipient.getEmail(),
                        buildFullName(recipient),
                        announcement.getTitle(),
                        announcement.getContent(),
                        buildFullName(announcement.getCreatedBy())
                );
            }
        }
    }

    private Set<Employee> resolveRecipients(Announcement announcement) {
        if (announcement.getTeam() == null) {
            return new LinkedHashSet<>(employeeRepository.findByStatus(UserStatus.ACTIVE));
        }

        Set<Employee> recipients = new LinkedHashSet<>();
        Team team = announcement.getTeam();
        if (team.getManager() != null && team.getManager().getStatus() == UserStatus.ACTIVE) {
            recipients.add(team.getManager());
        }
        List<TeamMember> activeMembers = teamMemberRepository.findByTeamIdAndLeftAtIsNull(team.getId());
        for (TeamMember teamMember : activeMembers) {
            if (teamMember.getEmployee().getStatus() == UserStatus.ACTIVE) {
                recipients.add(teamMember.getEmployee());
            }
        }
        return recipients;
    }

    private Announcement getAnnouncementOrThrow(Long announcementId) {
        return announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
    }

    private Employee resolveCreatorForAnnouncement() {
        Employee creator = authorizationService.getCurrentEmployeeOrThrow();
        if (creator.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Current employee must be active");
        }
        return creator;
    }

    private void ensureTenantAnnouncementPublisher() {
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        if (!(role.isTenantAdminEquivalent() || role.isHrEquivalent())) {
            throw new ForbiddenOperationException("Only tenant admins and HR can manage announcements");
        }
    }

    private Team resolveTeamOrNull(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));
    }

    private boolean canManageAnnouncement(Announcement announcement, AnnouncementAccessContext accessContext) {
        if (accessContext.tenantAdminEquivalent()) {
            return true;
        }
        if (!accessContext.hrEquivalent()) {
            return false;
        }
        return isOwnedByCurrentUser(announcement, accessContext.employeeId());
    }

    private void enforceAnnouncementReadAccess(Announcement announcement, AnnouncementAccessContext accessContext) {
        if (!canReadAnnouncement(announcement, accessContext)) {
            throw new ForbiddenOperationException("You are not allowed to view this announcement");
        }
    }

    private boolean canReadAnnouncement(Announcement announcement, AnnouncementAccessContext accessContext) {
        if (accessContext.privileged() || isOwnedByCurrentUser(announcement, accessContext.employeeId())) {
            return true;
        }
        if (announcement.getTeam() == null) {
            return true;
        }
        Long employeeId = accessContext.employeeId();
        if (employeeId == null) {
            return false;
        }

        Team team = announcement.getTeam();
        if (team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            return true;
        }
        return teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), employeeId)
                .isPresent();
    }

    private AnnouncementAccessContext resolveAnnouncementAccessContext() {
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        boolean privileged = role.isTenantAdminEquivalent() || role.isHrEquivalent();
        Employee employee = authorizationService.getCurrentEmployeeOrNull();
        Long employeeId = employee == null ? null : employee.getId();
        return new AnnouncementAccessContext(
                employeeId,
                privileged,
                role.isTenantAdminEquivalent(),
                role.isHrEquivalent()
        );
    }

    private AnnouncementResponseDto toResponse(Announcement announcement, AnnouncementAccessContext accessContext) {
        boolean ownedByCurrentUser = isOwnedByCurrentUser(announcement, accessContext.employeeId());
        boolean canManage = canManageAnnouncement(announcement, accessContext);
        String createdByName = trimToNull(announcement.getCreatedByName()) == null
                ? buildFullName(announcement.getCreatedBy())
                : announcement.getCreatedByName();
        return AnnouncementResponseDto.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .message(announcement.getContent())
                .createdByEmployeeId(announcement.getCreatedBy() == null ? null : announcement.getCreatedBy().getId())
                .createdByName(createdByName)
                .createdBy(tenantDtoMapper.toEmployeeSimple(announcement.getCreatedBy()))
                .createdByRole(announcement.getCreatedByRole())
                .pinned(announcement.isPinned())
                .teamId(announcement.getTeam() == null ? null : announcement.getTeam().getId())
                .teamName(announcement.getTeam() == null ? null : announcement.getTeam().getName())
                .ownedByCurrentUser(ownedByCurrentUser)
                .canEdit(canManage)
                .canDelete(canManage)
                .createdAt(announcement.getCreatedAt())
                .updatedAt(announcement.getUpdatedAt())
                .build();
    }

    private boolean isOwnedByCurrentUser(Announcement announcement, Long currentEmployeeId) {
        return currentEmployeeId != null
                && announcement.getCreatedBy() != null
                && currentEmployeeId.equals(announcement.getCreatedBy().getId());
    }

    private AnnouncementCreatorRole resolveCreatedByRole() {
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        if (role.isHrEquivalent()) {
            return AnnouncementCreatorRole.HR;
        }
        return AnnouncementCreatorRole.TENANT_ADMIN;
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

    private String resolveContentOrThrow(String value) {
        String content = trimToNull(value);
        if (content == null) {
            throw new BadRequestException("Announcement content is required");
        }
        if (content.length() > 5000) {
            throw new BadRequestException("Announcement content must not exceed 5000 characters");
        }
        return content;
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "title".equals(sortBy);
    }

    private String currentUserForLog() {
        try {
            return securityUtils.getCurrentUserEmailOrThrow();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private record AnnouncementAccessContext(
            Long employeeId,
            boolean privileged,
            boolean tenantAdminEquivalent,
            boolean hrEquivalent) {
    }
}
