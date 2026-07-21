package com.worknest.common.storage;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.LeaveRequest;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.HrMessageRepository;
import org.springframework.stereotype.Component;

@Component
public class StoredFileAccessPolicy {
    private final SecurityUtils securityUtils;
    private final AuthorizationService authorizationService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AnnouncementRepository announcementRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final HrMessageRepository hrMessageRepository;

    public StoredFileAccessPolicy(
            SecurityUtils securityUtils,
            AuthorizationService authorizationService,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            LeaveRequestRepository leaveRequestRepository,
            AnnouncementRepository announcementRepository,
            TeamMemberRepository teamMemberRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            HrMessageRepository hrMessageRepository) {
        this.securityUtils = securityUtils;
        this.authorizationService = authorizationService;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.announcementRepository = announcementRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.hrMessageRepository = hrMessageRepository;
    }

    public void requireRead(StoredFileMetadata file) {
        requireAccess(file, false);
    }

    public void requireWrite(StoredFileMetadata file) {
        requireAccess(file, true);
    }

    public void requireUpload(StorageCategory category) {
        securityUtils.getCurrentPrincipalOrThrow();
        switch (category) {
            case WORKSPACE_BANNER -> requirePermission(Permission.MANAGE_TENANT_SETTINGS);
            case CANDIDATE_RESUME -> requirePermission(Permission.MANAGE_RECRUITMENT);
            case ANNOUNCEMENT_ATTACHMENT -> requirePermission(Permission.SEND_ANNOUNCEMENT);
            case CHAT_ATTACHMENT -> requirePermission(Permission.CHAT_ACCESS);
            default -> {
                // Entity-specific access is enforced when the temporary upload is linked.
            }
        }
    }

    private void requireAccess(StoredFileMetadata file, boolean write) {
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();
        PlatformRole role = principal.getRole();
        if (role.isPlatformAdmin() || role.isTenantAdminEquivalent()) return;

        boolean owner = file.getUploadedByUserId() != null && file.getUploadedByUserId().equals(principal.getId());
        Long entityId = file.getRelatedEntityId();
        if (entityId == null) {
            if (owner) return;
            throw denied();
        }

        switch (file.getStorageCategory()) {
            case CANDIDATE_RESUME -> requirePermission(write ? Permission.MANAGE_RECRUITMENT : Permission.VIEW_RECRUITMENT);
            case WORKSPACE_BANNER -> {
                if (write) requirePermission(Permission.MANAGE_TENANT_SETTINGS);
            }
            case EMPLOYEE_AVATAR -> requireEmployeeAvatarAccess(entityId, owner, write);
            case TASK_ATTACHMENT -> requireTaskAccess(entityId, write);
            case PROJECT_ATTACHMENT -> requireProjectAccess(entityId, write);
            case ANNOUNCEMENT_ATTACHMENT -> requireAnnouncementAccess(entityId, write);
            case LEAVE_ATTACHMENT -> requireLeaveAccess(entityId, write);
            case CHAT_ATTACHMENT -> requireChatAccess(file, write);
            case IMAGE, DOCUMENT, TEMPORARY -> {
                if (!owner) throw denied();
            }
        }
    }

    private void requireEmployeeAvatarAccess(Long employeeId, boolean owner, boolean write) {
        Employee current = authorizationService.getCurrentEmployeeOrNull();
        boolean self = current != null && current.getId().equals(employeeId);
        // Tenant resolution plus the tenant persistence unit already isolate the row.
        // Any authenticated employee may read a coworker's avatar for collaboration UI.
        if (!write) return;
        if (!owner && !self && !authorizationService.hasPermission(Permission.MANAGE_EMPLOYEE)) throw denied();
    }

    private void requireTaskAccess(Long taskId, boolean write) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (!authorizationService.canAccessTask(task)) throw denied();
        if (write && authorizationService.getCurrentRoleOrThrow().isEmployeeOnly() && !authorizationService.isTaskAssignee(task)) throw denied();
    }

    private void requireProjectAccess(Long projectId, boolean write) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!authorizationService.canAccessProject(project)) throw denied();
        if (write
                && !authorizationService.hasPermission(Permission.MANAGE_PROJECT)
                && !authorizationService.hasAnyTeamRoleForProject(
                        projectId,
                        TeamFunctionalRole.PROJECT_MANAGER,
                        TeamFunctionalRole.TEAM_LEAD)) {
            throw denied();
        }
    }

    private void requireLeaveAccess(Long leaveId, boolean write) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        Employee current = authorizationService.getCurrentEmployeeOrNull();
        boolean owner = current != null && leave.getEmployee().getId().equals(current.getId());
        if (!owner && !authorizationService.hasPermission(Permission.APPROVE_LEAVE)) throw denied();
        if (write && !owner && !authorizationService.hasPermission(Permission.APPROVE_LEAVE)) throw denied();
    }

    private void requireAnnouncementAccess(Long announcementId, boolean write) {
        Announcement announcement = announcementRepository.findById(announcementId).orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
        if (write) {
            requirePermission(Permission.SEND_ANNOUNCEMENT);
            return;
        }
        Team team = announcement.getTeam();
        if (team == null) return;
        Employee current = authorizationService.getCurrentEmployeeOrNull();
        boolean participant = current != null && ((team.getManager() != null && current.getId().equals(team.getManager().getId()))
                || teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), current.getId()).isPresent());
        if (!participant && !authorizationService.getCurrentRoleOrThrow().isHrEquivalent()) throw denied();
    }

    private void requireChatAccess(StoredFileMetadata file, boolean write) {
        requirePermission(Permission.CHAT_ACCESS);
        Employee current = authorizationService.getCurrentEmployeeOrNull();
        if (current == null) throw denied();
        String module = file.getRelatedModule();
        if ("TEAM_CHAT_MESSAGE".equals(module)) {
            com.worknest.tenant.entity.TeamChatMessage message = teamChatMessageRepository.findById(file.getRelatedEntityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chat message not found"));
            Team team = message.getTeamChat().getTeam();
            boolean participant = (team.getManager() != null && current.getId().equals(team.getManager().getId()))
                    || teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), current.getId()).isPresent();
            if (!participant || (write && !message.getSender().getId().equals(current.getId()))) throw denied();
            return;
        }
        if ("HR_CHAT_MESSAGE".equals(module)) {
            com.worknest.tenant.entity.HrMessage message = hrMessageRepository.findById(file.getRelatedEntityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chat message not found"));
            PlatformRole role = authorizationService.getCurrentRoleOrThrow();
            boolean participant = role.isTenantAdminEquivalent() || role.isHrEquivalent()
                    || message.getConversation().getEmployee().getId().equals(current.getId());
            if (!participant || (write && !message.getSender().getId().equals(current.getId()))) throw denied();
            return;
        }
        throw denied();
    }

    private void requirePermission(Permission permission) {
        if (!authorizationService.hasPermission(permission)) throw denied();
    }

    private ForbiddenOperationException denied() {
        return new ForbiddenOperationException("You are not allowed to access this file");
    }
}
