package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.attachment.AttachmentCreateRequestDto;
import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Attachment;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.AttachmentRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.service.AttachmentService;
import com.worknest.tenant.service.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(transactionManager = "transactionManager")
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AnnouncementRepository announcementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public AttachmentServiceImpl(
            AttachmentRepository attachmentRepository,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            AnnouncementRepository announcementRepository,
            LeaveRequestRepository leaveRequestRepository,
            TeamMemberRepository teamMemberRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService,
            @Value("${app.storage.attachments-dir:storage/attachments}") String storageDir) {
        this.attachmentRepository = attachmentRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.announcementRepository = announcementRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public AttachmentResponseDto createAttachment(AttachmentCreateRequestDto requestDto) {
        validateCreateRequest(requestDto);
        AttachmentEntityType entityType = normalizeEntityType(requestDto.getEntityType());
        Employee uploader = authorizationService.getCurrentEmployeeOrThrow();
        validateEntityAccess(entityType, requestDto.getEntityId(), uploader, true);

        Attachment attachment = new Attachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(requestDto.getEntityId());
        attachment.setFileName(requestDto.getFileName().trim());
        attachment.setFileUrl(normalizeFileUrl(requestDto.getFileUrl()));
        attachment.setFileType(normalizeFileType(requestDto.getFileType()));
        attachment.setMimeType(attachment.getFileType());
        attachment.setFileSize(requestDto.getFileSize());
        attachment.setStoragePath(null);
        attachment.setUploadedBy(uploader);

        Attachment saved = attachmentRepository.save(attachment);
        auditLogService.logAction(
                AuditActionType.UPLOAD,
                AuditEntityType.ATTACHMENT,
                saved.getId(),
                "{\"entityType\":\"" + saved.getEntityType() + "\",\"entityId\":" + saved.getEntityId() + "}"
        );
        return toResponse(saved);
    }

    @Override
    public AttachmentResponseDto uploadAttachment(
            AttachmentEntityType entityType,
            Long entityId,
            String fileUrl,
            String fileType,
            String fileName,
            Long fileSize,
            MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            throw new BadRequestException(
                    "Direct file upload is no longer supported. Upload the file to external storage and submit fileUrl metadata instead."
            );
        }

        AttachmentCreateRequestDto requestDto = new AttachmentCreateRequestDto();
        requestDto.setEntityType(entityType);
        requestDto.setEntityId(entityId);
        requestDto.setFileUrl(fileUrl);
        requestDto.setFileType(fileType);
        requestDto.setFileName(fileName);
        requestDto.setFileSize(fileSize);
        return createAttachment(requestDto);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AttachmentResponseDto> listAttachments(AttachmentEntityType entityType, Long entityId) {
        AttachmentEntityType normalizedEntityType = normalizeEntityType(entityType);
        Employee currentEmployee = authorizationService.getCurrentEmployeeOrThrow();
        validateEntityAccess(normalizedEntityType, entityId, currentEmployee, false);

        return attachmentRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(normalizedEntityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AttachmentDownloadResult downloadAttachment(Long attachmentId) {
        Employee currentEmployee = authorizationService.getCurrentEmployeeOrThrow();
        Attachment attachment = getAttachmentOrThrow(attachmentId);
        validateEntityAccess(attachment.getEntityType(), attachment.getEntityId(), currentEmployee, false);

        auditLogService.logAction(
                AuditActionType.DOWNLOAD,
                AuditEntityType.ATTACHMENT,
                attachment.getId(),
                null
        );

        String fileUrl = trimToNull(attachment.getFileUrl());
        if (fileUrl != null) {
            return new AttachmentDownloadResult(null, attachment.getFileName(), resolveMimeType(attachment), fileUrl);
        }

        String storagePath = trimToNull(attachment.getStoragePath());
        if (storagePath == null) {
            throw new ResourceNotFoundException("Attachment file URL not found");
        }

        Path filePath = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!filePath.startsWith(storageRoot) || !Files.exists(filePath)) {
            throw new ResourceNotFoundException("Attachment file not found");
        }

        Resource resource = new FileSystemResource(filePath);
        return new AttachmentDownloadResult(resource, attachment.getFileName(), resolveMimeType(attachment), null);
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        Employee currentEmployee = authorizationService.getCurrentEmployeeOrThrow();
        Attachment attachment = getAttachmentOrThrow(attachmentId);
        validateEntityAccess(attachment.getEntityType(), attachment.getEntityId(), currentEmployee, true);

        boolean isOwner = attachment.getUploadedBy() != null
                && attachment.getUploadedBy().getId().equals(currentEmployee.getId());
        if (!isOwner && !hasDeletePermission(attachment.getEntityType())) {
            throw new ForbiddenOperationException("You are not allowed to delete this attachment");
        }

        deleteLegacyFileIfPresent(attachment.getStoragePath());
        attachmentRepository.delete(attachment);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.ATTACHMENT,
                attachmentId,
                null
        );
    }

    private Attachment getAttachmentOrThrow(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + attachmentId));
    }

    private void validateCreateRequest(AttachmentCreateRequestDto requestDto) {
        if (requestDto == null) {
            throw new BadRequestException("Attachment request is required");
        }
        if (requestDto.getEntityType() == null) {
            throw new BadRequestException("Attachment entity type is required");
        }
        if (requestDto.getEntityId() == null || requestDto.getEntityId() <= 0) {
            throw new BadRequestException("Attachment entity ID must be positive");
        }
        if (trimToNull(requestDto.getFileUrl()) == null) {
            throw new BadRequestException("fileUrl is required");
        }
        if (trimToNull(requestDto.getFileName()) == null) {
            throw new BadRequestException("fileName is required");
        }
        if (trimToNull(requestDto.getFileType()) == null) {
            throw new BadRequestException("fileType is required");
        }
        if (requestDto.getFileSize() == null || requestDto.getFileSize() <= 0) {
            throw new BadRequestException("fileSize must be positive");
        }
    }

    private void validateEntityAccess(AttachmentEntityType entityType, Long entityId, Employee currentEmployee, boolean write) {
        switch (normalizeEntityType(entityType)) {
            case TASK -> validateTaskAccess(entityId, currentEmployee, write);
            case PROJECT -> validateProjectAccess(entityId, write);
            case ANNOUNCEMENT -> validateAnnouncementAccess(entityId, currentEmployee, write);
            case LEAVE_REQUEST -> validateLeaveAccess(entityId, currentEmployee, write);
            case LEAVE -> throw new IllegalStateException("LEAVE must be normalized before validation");
        }
    }

    private void validateTaskAccess(Long taskId, Employee currentEmployee, boolean write) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
        if (!authorizationService.canAccessTask(task)) {
            throw new ForbiddenOperationException("You are not allowed to access attachments for this task");
        }
        if (write && authorizationService.getCurrentRoleOrThrow().isEmployeeOnly()
                && !authorizationService.isTaskAssignee(task)) {
            throw new ForbiddenOperationException("Employees can attach files only to tasks assigned to them");
        }
    }

    private void validateProjectAccess(Long projectId, boolean write) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        if (!authorizationService.canAccessProject(project)) {
            throw new ForbiddenOperationException("You are not allowed to access attachments for this project");
        }
        if (write && authorizationService.getCurrentRoleOrThrow().isEmployeeOnly()
                && !authorizationService.hasAnyTeamRoleForProject(projectId, com.worknest.tenant.enums.TeamFunctionalRole.PROJECT_MANAGER,
                com.worknest.tenant.enums.TeamFunctionalRole.TEAM_LEAD)) {
            throw new ForbiddenOperationException("Employees need project manager or team lead authority to attach files to this project");
        }
    }

    private void validateAnnouncementAccess(Long announcementId, Employee currentEmployee, boolean write) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));

        if (write) {
            authorizationService.requirePermission(Permission.SEND_ANNOUNCEMENT);
            return;
        }

        if (announcement.getTeam() == null) {
            return;
        }

        Team team = announcement.getTeam();
        boolean isParticipant = (team.getManager() != null && currentEmployee.getId().equals(team.getManager().getId()))
                || teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), currentEmployee.getId()).isPresent();
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        boolean privileged = role.isTenantAdminEquivalent() || role.isHrEquivalent();
        if (!privileged && !isParticipant) {
            throw new ForbiddenOperationException("You are not allowed to access attachments for this announcement");
        }
    }

    private void validateLeaveAccess(Long leaveRequestId, Employee currentEmployee, boolean write) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + leaveRequestId));

        boolean isOwner = leaveRequest.getEmployee().getId().equals(currentEmployee.getId());
        boolean privileged = authorizationService.hasPermission(Permission.APPROVE_LEAVE);
        if (!isOwner && !privileged) {
            throw new ForbiddenOperationException("You are not allowed to access attachments for this leave request");
        }
        if (write && !isOwner && !privileged) {
            throw new ForbiddenOperationException("You are not allowed to upload attachments for this leave request");
        }
    }

    private boolean hasDeletePermission(AttachmentEntityType entityType) {
        return switch (normalizeEntityType(entityType)) {
            case TASK -> authorizationService.hasPermission(Permission.MANAGE_TASK);
            case PROJECT -> authorizationService.hasPermission(Permission.MANAGE_PROJECT);
            case ANNOUNCEMENT -> authorizationService.hasPermission(Permission.SEND_ANNOUNCEMENT);
            case LEAVE_REQUEST -> authorizationService.hasPermission(Permission.APPROVE_LEAVE);
            case LEAVE -> false;
        };
    }

    private AttachmentEntityType normalizeEntityType(AttachmentEntityType entityType) {
        if (entityType == null) {
            throw new BadRequestException("Attachment entity type is required");
        }
        return entityType.canonical();
    }

    private void deleteLegacyFileIfPresent(String storagePath) {
        String normalizedStoragePath = trimToNull(storagePath);
        if (normalizedStoragePath == null) {
            return;
        }

        Path filePath = Paths.get(normalizedStoragePath).toAbsolutePath().normalize();
        if (!filePath.startsWith(storageRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Keep DB cleanup even if the old local file is already gone.
        }
    }

    private String normalizeFileUrl(String fileUrl) {
        String normalized = trimToNull(fileUrl);
        if (normalized == null) {
            throw new BadRequestException("fileUrl is required");
        }
        return normalized;
    }

    private String normalizeFileType(String fileType) {
        String normalized = trimToNull(fileType);
        if (normalized == null) {
            throw new BadRequestException("fileType is required");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String resolveMimeType(Attachment attachment) {
        String fileType = trimToNull(attachment.getFileType());
        if (fileType != null) {
            return fileType;
        }
        String mimeType = trimToNull(attachment.getMimeType());
        return mimeType == null ? "application/octet-stream" : mimeType;
    }

    private AttachmentResponseDto toResponse(Attachment attachment) {
        return AttachmentResponseDto.builder()
                .id(attachment.getId())
                .entityType(attachment.getEntityType())
                .entityId(attachment.getEntityId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileType(attachment.getFileType())
                .mimeType(resolveMimeType(attachment))
                .fileSize(attachment.getFileSize())
                .uploadedBy(tenantDtoMapper.toEmployeeSimple(attachment.getUploadedBy()))
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
