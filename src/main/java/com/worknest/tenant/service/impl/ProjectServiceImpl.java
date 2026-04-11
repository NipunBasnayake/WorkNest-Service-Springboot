package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.project.ProjectCreateRequestDto;
import com.worknest.tenant.dto.project.ProjectDetailResponseDto;
import com.worknest.tenant.dto.project.ProjectResponseDto;
import com.worknest.tenant.dto.project.ProjectStatusUpdateRequestDto;
import com.worknest.tenant.dto.project.ProjectTaskSummaryDto;
import com.worknest.tenant.dto.project.ProjectTeamAssignRequestDto;
import com.worknest.tenant.dto.project.ProjectTeamResponseDto;
import com.worknest.tenant.dto.project.ProjectUpdateRequestDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.AttachmentRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.ProjectService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(transactionManager = "transactionManager")
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;
    private final AttachmentRepository attachmentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TaskRepository taskRepository,
            AttachmentRepository attachmentRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public ProjectResponseDto createProject(ProjectCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CREATE_PROJECT);
        enforceProjectCreationAccess();
        validateProjectDates(requestDto.getStartDate(), requestDto.getEndDate());
        Employee creator = getActiveEmployeeOrThrow(authorizationService.getCurrentEmployeeIdOrThrow());

        Project project = new Project();
        project.setName(requestDto.getName().trim());
        project.setDescription(trimToNull(requestDto.getDescription()));
        project.setStartDate(requestDto.getStartDate());
        project.setEndDate(requestDto.getEndDate());
        project.setStatus(requestDto.getStatus() == null ? ProjectStatus.PLANNED : requestDto.getStatus());
        project.setCreatedBy(creator);

        Project saved = projectRepository.save(project);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.PROJECT,
                saved.getId(),
                "{\"name\":\"" + escapeJson(saved.getName()) + "\"}"
        );
        return toProjectResponse(saved);
    }

    @Override
    public ProjectResponseDto updateProject(Long projectId, ProjectUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectManagementAccess(project);

        validateProjectDates(requestDto.getStartDate(), requestDto.getEndDate());
        validateProjectStatusTransition(project.getStatus(), requestDto.getStatus());

        project.setName(requestDto.getName().trim());
        project.setDescription(trimToNull(requestDto.getDescription()));
        project.setStartDate(requestDto.getStartDate());
        project.setEndDate(requestDto.getEndDate());
        project.setStatus(requestDto.getStatus());

        Project updated = projectRepository.save(project);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.PROJECT,
                updated.getId(),
                "{\"name\":\"" + escapeJson(updated.getName()) + "\"}"
        );
        return toProjectResponse(updated);
    }

    @Override
    public ProjectResponseDto changeProjectStatus(Long projectId, ProjectStatusUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectManagementAccess(project);
        validateProjectStatusTransition(project.getStatus(), requestDto.getStatus());

        project.setStatus(requestDto.getStatus());
        Project updated = projectRepository.save(project);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.PROJECT,
                updated.getId(),
                "{\"status\":\"" + updated.getStatus() + "\"}"
        );
        return toProjectResponse(updated);
    }

    @Override
    public ProjectTeamResponseDto assignTeam(Long projectId, ProjectTeamAssignRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectManagementAccess(project);
        ensureProjectAllowsTeamAssignment(project);
        Team team = getTeamOrThrow(requestDto.getTeamId());

        if (projectTeamRepository.existsByProjectIdAndTeamId(project.getId(), team.getId())) {
            throw new BadRequestException("Team is already assigned to project");
        }

        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProject(project);
        projectTeam.setTeam(team);

        ProjectTeam saved = projectTeamRepository.save(projectTeam);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.PROJECT,
                projectId,
                "{\"teamId\":" + requestDto.getTeamId() + "}"
        );
        return toProjectTeamResponse(saved);
    }

    @Override
    public void removeTeamAssignment(Long projectId, Long teamId) {
        authorizationService.requirePermission(Permission.MANAGE_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectManagementAccess(project);
        ProjectTeam projectTeam = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Project-team assignment not found"));
        projectTeamRepository.delete(projectTeam);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.PROJECT,
                projectId,
                "{\"teamId\":" + teamId + "}"
        );
    }

    @Override
    public void deleteProject(Long projectId) {
        authorizationService.requirePermission(Permission.MANAGE_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectManagementAccess(project);
        long taskCount = taskRepository.countByProjectId(projectId);
        if (taskCount > 0) {
            throw new BadRequestException("Cannot delete project with existing tasks. Delete tasks first.");
        }

        if (attachmentRepository.existsByEntityTypeAndEntityId(AttachmentEntityType.PROJECT, projectId)) {
            throw new BadRequestException("Cannot delete project with attachments. Remove attachments first.");
        }

        projectTeamRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.PROJECT,
                projectId,
                "{\"name\":\"" + escapeJson(project.getName()) + "\"}"
        );
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ProjectResponseDto> listProjects() {
        authorizationService.requirePermission(Permission.VIEW_PROJECT);
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        if (role == PlatformRole.EMPLOYEE) {
            Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
            List<Project> readableProjects = listReadableProjectsForEmployee(currentEmployeeId);
            readableProjects.sort(projectComparator("createdAt", Sort.Direction.DESC));
            return readableProjects.stream()
                    .map(this::toProjectResponse)
                    .toList();
        }

        return projectRepository.findAll().stream()
                .map(this::toProjectResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ProjectResponseDto> listMyProjects() {
        authorizationService.requirePermission(Permission.VIEW_PROJECT);
        Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
        List<Project> myProjects = listReadableProjectsForEmployee(currentEmployeeId);
        myProjects.sort(projectComparator("createdAt", Sort.Direction.DESC));
        return myProjects.stream()
                .map(this::toProjectResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<ProjectResponseDto> listProjectsPaged(
            ProjectStatus status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.VIEW_PROJECT);
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        if (role == PlatformRole.EMPLOYEE) {
            Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
            Page<Project> resultPage = projectRepository.searchReadableByEmployee(
                    currentEmployeeId,
                    status,
                    search,
                    PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy)));

            return PagedResultDto.<ProjectResponseDto>builder()
                    .items(resultPage.getContent().stream().map(this::toProjectResponse).toList())
                    .page(resultPage.getNumber())
                    .size(resultPage.getSize())
                    .totalElements(resultPage.getTotalElements())
                    .totalPages(resultPage.getTotalPages())
                    .build();
        }

        Page<Project> resultPage = projectRepository.search(
                status,
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<ProjectResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toProjectResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public ProjectDetailResponseDto getProjectDetails(Long projectId) {
        authorizationService.requirePermission(Permission.VIEW_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);
        List<ProjectTeamResponseDto> teams = listProjectTeams(projectId);
        ProjectTaskSummaryDto taskSummary = buildProjectTaskSummary(projectId);

        return ProjectDetailResponseDto.builder()
                .project(toProjectResponse(project))
                .teams(teams)
                .taskSummary(taskSummary)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ProjectTeamResponseDto> listProjectTeams(Long projectId) {
        authorizationService.requirePermission(Permission.VIEW_PROJECT);
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);
        return projectTeamRepository.findByProjectId(projectId).stream()
                .map(this::toProjectTeamResponse)
                .toList();
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }

    private Team getTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));
    }

    private Employee getActiveEmployeeOrThrow(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (employee.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Employee is not active: " + employeeId);
        }
        return employee;
    }

    private ProjectTaskSummaryDto buildProjectTaskSummary(Long projectId) {
        long totalTasks = taskRepository.countByProjectId(projectId);
        long todoTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.TODO);
        long inProgressTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS);
        long inReviewTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.IN_REVIEW);
        long doneTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
        long blockedTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.BLOCKED);
        long overdueTasks = taskRepository.countByProjectIdAndDueDateBeforeAndStatusNot(
                projectId,
                LocalDate.now(),
                TaskStatus.DONE
        );

        return ProjectTaskSummaryDto.builder()
                .totalTasks(totalTasks)
                .todoTasks(todoTasks)
                .inProgressTasks(inProgressTasks)
                .inReviewTasks(inReviewTasks)
                .doneTasks(doneTasks)
                .blockedTasks(blockedTasks)
                .overdueTasks(overdueTasks)
                .completionRatePercent(calculatePercent(doneTasks, totalTasks))
                .build();
    }

    private void validateProjectDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BadRequestException("Project start date is required");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("Project end date cannot be before start date");
        }
    }

    private void validateProjectStatusTransition(ProjectStatus currentStatus, ProjectStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return;
        }

        if ((currentStatus == ProjectStatus.COMPLETED || currentStatus == ProjectStatus.CANCELLED)
                && currentStatus != newStatus) {
            throw new BadRequestException("Cannot change status after project is " + currentStatus);
        }
    }

    private void ensureProjectAllowsTeamAssignment(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.CANCELLED) {
            throw new BadRequestException("Cannot assign teams to a project in status: " + project.getStatus());
        }
    }

    private ProjectResponseDto toProjectResponse(Project project) {
        return ProjectResponseDto.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .status(project.getStatus())
                .createdBy(tenantDtoMapper.toEmployeeSimple(project.getCreatedBy()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ProjectTeamResponseDto toProjectTeamResponse(ProjectTeam projectTeam) {
        return ProjectTeamResponseDto.builder()
                .id(projectTeam.getId())
                .projectId(projectTeam.getProject().getId())
                .teamId(projectTeam.getTeam().getId())
                .teamName(projectTeam.getTeam().getName())
                .build();
    }

    private List<Project> listReadableProjectsForEmployee(Long employeeId) {
        Map<Long, Project> readableProjects = new LinkedHashMap<>();

        projectRepository.findByCreatedById(employeeId)
                .forEach(project -> readableProjects.put(project.getId(), project));

        Set<Long> employeeTeamIds = getEmployeeTeamIds(employeeId);
        if (!employeeTeamIds.isEmpty()) {
            projectRepository.findDistinctByAssignedTeamIds(new ArrayList<>(employeeTeamIds))
                    .forEach(project -> readableProjects.put(project.getId(), project));
        }

        List<Long> participantProjectIds = taskRepository.findDistinctProjectIdsByParticipantEmployeeId(employeeId);
        if (!participantProjectIds.isEmpty()) {
            projectRepository.findAllById(participantProjectIds)
                    .forEach(project -> readableProjects.put(project.getId(), project));
        }

        return new ArrayList<>(readableProjects.values());
    }

    private Set<Long> getEmployeeTeamIds(Long employeeId) {
        Set<Long> teamIds = new LinkedHashSet<>();

        teamRepository.findByManagerId(employeeId)
                .stream()
                .map(Team::getId)
                .forEach(teamIds::add);

        teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(employeeId)
                .stream()
                .map(teamMember -> teamMember.getTeam().getId())
                .forEach(teamIds::add);

        return teamIds;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) ||
                "updatedAt".equals(sortBy) ||
                "name".equals(sortBy) ||
                "startDate".equals(sortBy) ||
                "endDate".equals(sortBy) ||
                "status".equals(sortBy);
    }

    private void enforceProjectReadAccess(Project project) {
        if (!authorizationService.canAccessProject(project)) {
            throw new ForbiddenOperationException("You are not allowed to access this project");
        }
    }

    private boolean canReadProject(Project project, PlatformRole role, Long currentEmployeeId) {
        if (role != PlatformRole.EMPLOYEE) {
            return true;
        }
        if (currentEmployeeId == null) {
            return false;
        }

        if (project.getCreatedBy() != null && currentEmployeeId.equals(project.getCreatedBy().getId())) {
            return true;
        }

        if (taskRepository.existsByProjectIdAndParticipantEmployeeId(project.getId(), currentEmployeeId)) {
            return true;
        }

        Set<Long> employeeTeamIds = getEmployeeTeamIds(currentEmployeeId);
        if (employeeTeamIds.isEmpty()) {
            return false;
        }

        return projectTeamRepository.findByProjectId(project.getId()).stream()
                .map(projectTeam -> projectTeam.getTeam().getId())
                .anyMatch(employeeTeamIds::contains);
    }

    private void enforceProjectCreationAccess() {
        if (authorizationService.isTenantAdminEquivalent()) {
            return;
        }
        if (!hasAnyActiveTeamRole(TeamFunctionalRole.PROJECT_MANAGER)) {
            throw new ForbiddenOperationException("Only project managers can create projects");
        }
    }

    private void enforceProjectManagementAccess(Project project) {
        if (authorizationService.isTenantAdminEquivalent()) {
            return;
        }
        if (authorizationService.hasAnyTeamRoleForProject(project.getId(), TeamFunctionalRole.PROJECT_MANAGER)) {
            return;
        }
        if (projectTeamRepository.findByProjectId(project.getId()).isEmpty()
                && hasAnyActiveTeamRole(TeamFunctionalRole.PROJECT_MANAGER)) {
            return;
        }
        throw new ForbiddenOperationException("Only project managers can manage this project");
    }

    private boolean hasAnyActiveTeamRole(TeamFunctionalRole... roles) {
        Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null || roles == null || roles.length == 0) {
            return false;
        }
        Set<TeamFunctionalRole> allowedRoles = Set.of(roles);
        return teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(currentEmployeeId).stream()
                .map(teamMember -> teamMember.getFunctionalRole())
                .anyMatch(allowedRoles::contains);
    }

    private double calculatePercent(long part, long total) {
        if (total <= 0) {
            return 0d;
        }
        double percentage = ((double) part * 100.0) / total;
        return Math.round(percentage * 100.0) / 100.0;
    }

    private Comparator<Project> projectComparator(String sortBy, Sort.Direction direction) {
        Comparator<Project> comparator = switch (sortBy) {
            case "updatedAt" -> Comparator.comparing(
                    Project::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "name" -> Comparator.comparing(
                    Project::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "startDate" -> Comparator.comparing(
                    Project::getStartDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "endDate" -> Comparator.comparing(
                    Project::getEndDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "status" -> Comparator.comparing(
                    Project::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                    Project::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        return direction.isAscending() ? comparator : comparator.reversed();
    }
}
