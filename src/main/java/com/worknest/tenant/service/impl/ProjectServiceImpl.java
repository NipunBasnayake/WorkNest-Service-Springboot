package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.project.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.ProjectService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EmployeeRepository employeeRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.employeeRepository = employeeRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public ProjectResponseDto createProject(ProjectCreateRequestDto requestDto) {
        validateProjectDates(requestDto.getStartDate(), requestDto.getEndDate());
        validateActorEmployeeId(requestDto.getCreatedByEmployeeId());

        Employee creator = getActiveEmployeeOrThrow(requestDto.getCreatedByEmployeeId());

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
        Project project = getProjectOrThrow(projectId);

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
        Project project = getProjectOrThrow(projectId);
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
        Project project = getProjectOrThrow(projectId);
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
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ProjectResponseDto> listProjects() {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        Long currentEmployeeId = role == PlatformRole.EMPLOYEE ? resolveCurrentEmployeeIdOrThrow() : null;

        return projectRepository.findAll().stream()
                .filter(project -> canReadProject(project, role, currentEmployeeId))
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
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        Long currentEmployeeId = role == PlatformRole.EMPLOYEE ? resolveCurrentEmployeeIdOrThrow() : null;

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        if (role == PlatformRole.EMPLOYEE) {
            List<Project> filteredProjects = projectRepository.search(status, trimToNull(search), Pageable.unpaged())
                    .getContent()
                    .stream()
                    .filter(project -> canReadProject(project, role, currentEmployeeId))
                    .toList();

            filteredProjects = new java.util.ArrayList<>(filteredProjects);
            filteredProjects.sort(projectComparator(resolvedSortBy, direction));

            List<ProjectResponseDto> filtered = filteredProjects.stream()
                    .map(this::toProjectResponse)
                    .toList();

            int fromIndex = Math.min(resolvedPage * resolvedSize, filtered.size());
            int toIndex = Math.min(fromIndex + resolvedSize, filtered.size());
            long totalElements = filtered.size();
            int totalPages = resolvedSize == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);

            return PagedResultDto.<ProjectResponseDto>builder()
                    .items(filtered.subList(fromIndex, toIndex))
                    .page(resolvedPage)
                    .size(resolvedSize)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
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
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);
        List<ProjectTeamResponseDto> teams = listProjectTeams(projectId);

        return ProjectDetailResponseDto.builder()
                .project(toProjectResponse(project))
                .teams(teams)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ProjectTeamResponseDto> listProjectTeams(Long projectId) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void validateActorEmployeeId(Long actorEmployeeId) {
        String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
        employeeRepository.findByEmailIgnoreCase(currentEmail).ifPresentOrElse(currentEmployee -> {
                    if (!currentEmployee.getId().equals(actorEmployeeId)) {
                        throw new ForbiddenOperationException("Authenticated user cannot act on behalf of another employee");
                    }
                },
                () -> {
                    if (securityUtils.getCurrentRoleOrThrow() != PlatformRole.TENANT_ADMIN) {
                        throw new ForbiddenOperationException("Current user is not linked to an employee profile");
                    }
                });
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
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return;
        }

        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        if (!canReadProject(project, role, currentEmployeeId)) {
            throw new ForbiddenOperationException("Employees cannot access this project");
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

        return projectTeamRepository.findByProjectId(project.getId()).stream()
                .anyMatch(projectTeam -> {
                    Team team = projectTeam.getTeam();
                    if (team.getManager() != null && currentEmployeeId.equals(team.getManager().getId())) {
                        return true;
                    }
                    return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(
                            team.getId(),
                            currentEmployeeId
                    ).isPresent();
                });
    }

    private Long resolveCurrentEmployeeIdOrThrow() {
        String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
        Employee employee = employeeRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new ForbiddenOperationException("Current user is not linked to an employee profile"));
        return employee.getId();
    }

    private java.util.Comparator<Project> projectComparator(String sortBy, Sort.Direction direction) {
        java.util.Comparator<Project> comparator = switch (sortBy) {
            case "updatedAt" -> java.util.Comparator.comparing(Project::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "name" -> java.util.Comparator.comparing(Project::getName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "startDate" -> java.util.Comparator.comparing(Project::getStartDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "endDate" -> java.util.Comparator.comparing(Project::getEndDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "status" -> java.util.Comparator.comparing(Project::getStatus, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            default -> java.util.Comparator.comparing(Project::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        };
        return direction.isAscending() ? comparator : comparator.reversed();
    }
}
