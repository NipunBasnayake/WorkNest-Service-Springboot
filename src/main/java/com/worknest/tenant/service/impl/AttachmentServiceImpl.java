package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.entity.*;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.repository.*;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager")
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AnnouncementRepository announcementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;
    private final Path storageRoot;
    private final long maxFileSizeBytes;
    private final Set<String> allowedMimeTypes;

    public AttachmentServiceImpl(
            AttachmentRepository attachmentRepository,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            AnnouncementRepository announcementRepository,
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamMemberRepository teamMemberRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService,
            @Value("${app.storage.attachments-dir:storage/attachments}") String storageDir,
            @Value("${app.storage.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            @Value("${app.storage.allowed-mime-types:image/png,image/jpeg,application/pdf,text/plain}") String allowedMimeTypesRaw) {
        this.attachmentRepository = attachmentRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.announcementRepository = announcementRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedMimeTypes = parseAllowedMimeTypes(allowedMimeTypesRaw);
    }

    @Override
    public AttachmentResponseDto uploadAttachment(AttachmentEntityType entityType, Long entityId, MultipartFile file) {
        validateFile(file);
        Employee uploader = getCurrentEmployeeOrThrow();

        validateEntityAccess(entityType, entityId, uploader, true);

        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        String sanitizedName = sanitizeFileName(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + "_" + sanitizedName;

        Path targetFile = resolveTargetPath(tenantKey, entityType, entityId, storedFileName);
        try {
            Files.createDirectories(targetFile.getParent());
            file.transferTo(targetFile.toFile());
        } catch (IOException ex) {
            throw new BadRequestException("Failed to store attachment file");
        }

        Attachment attachment = new Attachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFileName(sanitizedName);
        attachment.setMimeType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setStoragePath(targetFile.toString());
        attachment.setUploadedBy(uploader);

        Attachment saved = attachmentRepository.save(attachment);
        auditLogService.logAction(
                AuditActionType.UPLOAD,
                AuditEntityType.ATTACHMENT,
                saved.getId(),
                "{\"entityType\":\"" + entityType + "\",\"entityId\":" + entityId + "}"
        );

        return toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<AttachmentResponseDto> listAttachments(AttachmentEntityType entityType, Long entityId) {
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        validateEntityAccess(entityType, entityId, currentEmployee, false);

        return attachmentRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public AttachmentDownloadResult downloadAttachment(Long attachmentId) {
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        Attachment attachment = getAttachmentOrThrow(attachmentId);

        validateEntityAccess(attachment.getEntityType(), attachment.getEntityId(), currentEmployee, false);

        Path filePath = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(storageRoot) || !Files.exists(filePath)) {
            throw new ResourceNotFoundException("Attachment file not found");
        }

        auditLogService.logAction(
                AuditActionType.DOWNLOAD,
                AuditEntityType.ATTACHMENT,
                attachment.getId(),
                null
        );

        Resource resource = new FileSystemResource(filePath);
        return new AttachmentDownloadResult(resource, attachment.getFileName(), attachment.getMimeType());
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        Attachment attachment = getAttachmentOrThrow(attachmentId);

        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        boolean isOwner = attachment.getUploadedBy().getId().equals(currentEmployee.getId());
        boolean isPrivileged = role == PlatformRole.TENANT_ADMIN || role == PlatformRole.ADMIN || role == PlatformRole.HR;

        if (!isOwner && !isPrivileged) {
            throw new ForbiddenOperationException("You are not allowed to delete this attachment");
        }

        Path filePath = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        if (filePath.startsWith(storageRoot)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
                // Keep DB cleanup even if file was already gone.
            }
        }

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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("File exceeds maximum size limit");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !allowedMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Unsupported file type");
        }
    }

    private void validateEntityAccess(AttachmentEntityType entityType, Long entityId, Employee currentEmployee, boolean write) {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        switch (entityType) {
            case TASK -> {
                Task task = taskRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + entityId));
                if (!canAccessTask(task, currentEmployee, role)) {
                    throw new ForbiddenOperationException("You are not allowed to access attachments for this task");
                }
            }
            case PROJECT -> {
                Project project = projectRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + entityId));
                if (!canAccessProject(project, currentEmployee, role)) {
                    throw new ForbiddenOperationException("You are not allowed to access attachments for this project");
                }
            }
            case ANNOUNCEMENT -> {
                announcementRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + entityId));
                if (write && !isPrivilegedRole(role)) {
                    throw new ForbiddenOperationException("Only privileged roles can attach files to announcements");
                }
            }
            case LEAVE_REQUEST -> {
                LeaveRequest leaveRequest = leaveRequestRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Leave request not found with id: " + entityId));
                boolean isOwner = leaveRequest.getEmployee().getId().equals(currentEmployee.getId());
                boolean privileged = isPrivilegedRole(role);

                if (!isOwner && !privileged) {
                    throw new ForbiddenOperationException("You are not allowed to access attachments for this leave request");
                }
                if (write && role == PlatformRole.EMPLOYEE && !isOwner) {
                    throw new ForbiddenOperationException("Employees can upload only to their own leave requests");
                }
            }
        }
    }

    private Employee getCurrentEmployeeOrThrow() {
        String email = securityUtils.getCurrentUserEmailOrThrow();
        return employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }

    private Path resolveTargetPath(
            String tenantKey,
            AttachmentEntityType entityType,
            Long entityId,
            String storedFileName) {

        Path tenantDir = storageRoot
                .resolve(tenantKey)
                .resolve(entityType.name().toLowerCase(Locale.ROOT))
                .resolve(String.valueOf(entityId))
                .normalize();

        if (!tenantDir.startsWith(storageRoot)) {
            throw new ForbiddenOperationException("Invalid attachment path");
        }

        Path target = tenantDir.resolve(storedFileName).normalize();
        if (!target.startsWith(tenantDir)) {
            throw new ForbiddenOperationException("Invalid attachment file path");
        }

        return target;
    }

    private String sanitizeFileName(String originalFileName) {
        String fallback = "file.bin";
        if (originalFileName == null || originalFileName.isBlank()) {
            return fallback;
        }

        String baseName = Paths.get(originalFileName).getFileName().toString();
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private boolean isPrivilegedRole(PlatformRole role) {
        return role == PlatformRole.TENANT_ADMIN || role == PlatformRole.ADMIN || role == PlatformRole.HR;
    }

    private boolean canAccessTask(Task task, Employee currentEmployee, PlatformRole role) {
        if (isPrivilegedRole(role)) {
            return true;
        }
        if (task.getCreatedBy().getId().equals(currentEmployee.getId())) {
            return true;
        }
        if (task.getAssignee() != null && task.getAssignee().getId().equals(currentEmployee.getId())) {
            return true;
        }
        return canAccessProject(task.getProject(), currentEmployee, role);
    }

    private boolean canAccessProject(Project project, Employee currentEmployee, PlatformRole role) {
        if (isPrivilegedRole(role)) {
            return true;
        }
        if (project.getCreatedBy().getId().equals(currentEmployee.getId())) {
            return true;
        }

        List<ProjectTeam> projectTeams = projectTeamRepository.findByProjectId(project.getId());
        List<Long> teamIds = projectTeams.stream()
                .map(projectTeam -> projectTeam.getTeam().getId())
                .toList();
        if (teamIds.isEmpty()) {
            return false;
        }

        boolean isActiveMember = teamIds.stream()
                .anyMatch(teamId -> teamMemberRepository
                        .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, currentEmployee.getId())
                        .isPresent());
        if (isActiveMember) {
            return true;
        }

        return projectTeams.stream()
                .map(ProjectTeam::getTeam)
                .filter(team -> team.getManager() != null)
                .anyMatch(team -> team.getManager().getId().equals(currentEmployee.getId()));
    }

    private Set<String> parseAllowedMimeTypes(String raw) {
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private AttachmentResponseDto toResponse(Attachment attachment) {
        return AttachmentResponseDto.builder()
                .id(attachment.getId())
                .entityType(attachment.getEntityType())
                .entityId(attachment.getEntityId())
                .fileName(attachment.getFileName())
                .mimeType(attachment.getMimeType())
                .fileSize(attachment.getFileSize())
                .uploadedBy(tenantDtoMapper.toEmployeeSimple(attachment.getUploadedBy()))
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
