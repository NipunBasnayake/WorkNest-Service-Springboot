package com.worknest.security.authorization;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.security.service.CurrentUserService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private final SecurityUtils securityUtils;
    private final CurrentUserService currentUserService;
    private final RolePermissionMatrix rolePermissionMatrix;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;

    public AuthorizationServiceImpl(
            SecurityUtils securityUtils,
            CurrentUserService currentUserService,
            RolePermissionMatrix rolePermissionMatrix,
            TeamMemberRepository teamMemberRepository,
            ProjectTeamRepository projectTeamRepository,
            TaskRepository taskRepository) {
        this.securityUtils = securityUtils;
        this.currentUserService = currentUserService;
        this.rolePermissionMatrix = rolePermissionMatrix;
        this.teamMemberRepository = teamMemberRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return rolePermissionMatrix.hasPermission(getCurrentRoleOrThrow(), permission);
    }

    @Override
    public void requirePermission(Permission permission) {
        if (!hasPermission(permission)) {
            throw new ForbiddenOperationException("Current user does not have permission: " + permission);
        }
    }

    @Override
    public boolean isTaskAssignee(Task task) {
        if (task == null || task.getAssignee() == null) {
            return false;
        }
        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        return currentEmployeeId != null && currentEmployeeId.equals(task.getAssignee().getId());
    }

    @Override
    public boolean isTeamMember(Long teamId) {
        if (teamId == null) {
            return false;
        }
        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            return false;
        }
        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, currentEmployeeId).isPresent();
    }

    @Override
    public boolean hasTeamRole(Long teamId, TeamFunctionalRole... roles) {
        if (teamId == null || roles == null || roles.length == 0) {
            return false;
        }
        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            return false;
        }

        Set<TeamFunctionalRole> requestedRoles = EnumSet.copyOf(Arrays.asList(roles));
        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, currentEmployeeId)
                .map(TeamMember::getFunctionalRole)
                .map(requestedRoles::contains)
                .orElse(false);
    }

    @Override
    public Employee getCurrentEmployeeOrThrow() {
        return currentUserService.getCurrentEmployeeOrThrow();
    }

    @Override
    public Employee getCurrentEmployeeOrNull() {
        return currentUserService.getCurrentEmployeeOrNull();
    }

    @Override
    public Long getCurrentEmployeeIdOrThrow() {
        return currentUserService.getCurrentEmployeeIdOrThrow();
    }

    @Override
    public Long getCurrentEmployeeIdOrNull() {
        return currentUserService.getCurrentEmployeeIdOrNull();
    }

    @Override
    public Long getCurrentUserIdOrThrow() {
        Long userId = securityUtils.getCurrentPrincipalOrThrow().getId();
        if (userId == null) {
            throw new ForbiddenOperationException("Authenticated user id is required");
        }
        return userId;
    }

    @Override
    public String getCurrentTenantKeyOrThrow() {
        return securityUtils.getCurrentTenantKeyOrThrow();
    }

    @Override
    public PlatformRole getCurrentRoleOrThrow() {
        return securityUtils.getCurrentRoleOrThrow();
    }

    @Override
    public boolean isTenantAdminEquivalent() {
        return getCurrentRoleOrThrow().isTenantAdminEquivalent();
    }

    @Override
    public boolean hasAnyTeamRoleForProject(Long projectId, TeamFunctionalRole... roles) {
        if (projectId == null || roles == null || roles.length == 0) {
            return false;
        }
        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            return false;
        }

        Set<TeamFunctionalRole> requestedRoles = EnumSet.copyOf(Arrays.asList(roles));
        List<ProjectTeam> projectTeams = projectTeamRepository.findByProjectId(projectId);
        if (projectTeams.isEmpty()) {
            return teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(currentEmployeeId).stream()
                    .map(TeamMember::getFunctionalRole)
                    .anyMatch(requestedRoles::contains);
        }

        return projectTeams.stream()
                .map(ProjectTeam::getTeam)
                .map(Team::getId)
                .anyMatch(teamId -> teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, currentEmployeeId)
                        .map(TeamMember::getFunctionalRole)
                        .map(requestedRoles::contains)
                        .orElse(false));
    }

    @Override
    public boolean canAccessProject(Project project) {
        if (project == null) {
            return false;
        }
        if (!getCurrentRoleOrThrow().isEmployeeOnly()) {
            return true;
        }

        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            return false;
        }
        if (project.getCreatedBy() != null && currentEmployeeId.equals(project.getCreatedBy().getId())) {
            return true;
        }

        if (taskRepository.existsByProjectIdAndParticipantEmployeeId(project.getId(), currentEmployeeId)) {
            return true;
        }

        Set<Long> employeeTeamIds = teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(currentEmployeeId).stream()
                .map(TeamMember::getTeam)
                .map(Team::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (employeeTeamIds.isEmpty()) {
            return false;
        }

        return projectTeamRepository.findByProjectId(project.getId()).stream()
                .map(ProjectTeam::getTeam)
                .map(Team::getId)
                .anyMatch(employeeTeamIds::contains);
    }

    @Override
    public boolean canAccessTask(Task task) {
        if (task == null) {
            return false;
        }
        if (!getCurrentRoleOrThrow().isEmployeeOnly()) {
            return true;
        }
        return isTaskAssignee(task) || canAccessProject(task.getProject());
    }

    @Override
    public boolean canAccessTeam(Team team) {
        if (team == null) {
            return false;
        }
        if (!getCurrentRoleOrThrow().isEmployeeOnly()) {
            return true;
        }

        Long currentEmployeeId = getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            return false;
        }
        if (team.getManager() != null && currentEmployeeId.equals(team.getManager().getId())) {
            return true;
        }
        return isTeamMember(team.getId());
    }
}
