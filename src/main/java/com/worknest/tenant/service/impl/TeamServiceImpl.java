package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.team.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.TeamChatRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.TeamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(transactionManager = "transactionManager")
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamChatRepository teamChatRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;
    private final EmailNotificationService emailNotificationService;

    public TeamServiceImpl(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamChatRepository teamChatRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService,
            EmailNotificationService emailNotificationService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamChatRepository = teamChatRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public TeamResponseDto createTeam(TeamCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CREATE_TEAM);
        String teamName = normalizeTeamName(requestDto.getName());
        if (teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        Team team = new Team();
        team.setName(teamName);
        team.setManager(manager);

        Team saved = teamRepository.save(team);
        ensureManagerMembership(saved, manager);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.TEAM,
                saved.getId(),
                "{\"name\":\"" + escapeJson(saved.getName()) + "\"}"
        );
        return toTeamResponse(saved);
    }

    @Override
    public TeamResponseDto updateTeam(Long teamId, TeamUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_TEAM);
        Team team = getTeamOrThrow(teamId);
        String teamName = normalizeTeamName(requestDto.getName());

        if (!team.getName().equalsIgnoreCase(teamName) && teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        team.setName(teamName);
        team.setManager(manager);

        Team updated = teamRepository.save(team);
        ensureManagerMembership(updated, manager);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TEAM,
                updated.getId(),
                "{\"name\":\"" + escapeJson(updated.getName()) + "\"}"
        );
        return toTeamResponse(updated);
    }

    @Override
    public TeamResponseDto changeManager(Long teamId, Long managerEmployeeId) {
        authorizationService.requirePermission(Permission.MANAGE_TEAM);
        Team team = getTeamOrThrow(teamId);
        Employee manager = getActiveEmployeeOrThrow(managerEmployeeId);
        team.setManager(manager);
        Team updated = teamRepository.save(team);
        ensureManagerMembership(updated, manager);
        notifyTeamLeaderChange(updated);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TEAM,
                updated.getId(),
                "{\"managerId\":" + manager.getId() + "}"
        );
        return toTeamResponse(updated);
    }

    @Override
    public TeamMemberResponseDto addMember(Long teamId, TeamMemberAddRequestDto requestDto) {
        authorizationService.requirePermission(Permission.ASSIGN_TEAM_ROLE);
        Team team = getTeamOrThrow(teamId);
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());

        if (teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employee.getId()).isPresent()) {
            throw new BadRequestException("Employee is already an active member of this team");
        }

        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setEmployee(employee);
        teamMember.setFunctionalRole(
                requestDto.getFunctionalRole() == null ? TeamFunctionalRole.MEMBER : requestDto.getFunctionalRole());
        teamMember.setJoinedAt(LocalDateTime.now());

        TeamMember savedMember = teamMemberRepository.save(teamMember);
        emailNotificationService.sendTeamAddedEmail(
                employee.getEmail(),
                buildFullName(employee),
                team.getName(),
                buildFullName(team.getManager()),
                (int) teamMemberRepository.countByTeamIdAndLeftAtIsNull(teamId)
        );
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employee.getId() + ",\"functionalRole\":\"" + savedMember.getFunctionalRole() + "\"}"
        );
        return toTeamMemberResponse(savedMember);
    }

    @Override
    public TeamMemberResponseDto updateMemberFunctionalRole(
            Long teamId,
            Long employeeId,
            TeamMemberRoleUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.ASSIGN_TEAM_ROLE);
        getTeamOrThrow(teamId);

        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Active team membership not found"));

        teamMember.setFunctionalRole(requestDto.getFunctionalRole());
        TeamMember updated = teamMemberRepository.save(teamMember);

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employeeId + ",\"functionalRole\":\"" + updated.getFunctionalRole() + "\"}"
        );
        return toTeamMemberResponse(updated);
    }

    @Override
    public void removeMember(Long teamId, Long employeeId) {
        authorizationService.requirePermission(Permission.MANAGE_TEAM);
        Team team = getTeamOrThrow(teamId);
        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Active team membership not found"));

        if (team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            throw new BadRequestException("Cannot remove the current team manager from members. Reassign manager first.");
        }

        teamMember.setLeftAt(LocalDateTime.now());
        teamMemberRepository.save(teamMember);
        emailNotificationService.sendTeamRemovedEmail(
                teamMember.getEmployee().getEmail(),
                buildFullName(teamMember.getEmployee()),
                team.getName()
        );
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employeeId + "}"
        );
    }

    @Override
    public void deleteTeam(Long teamId) {
        authorizationService.requirePermission(Permission.MANAGE_TEAM);
        Team team = getTeamOrThrow(teamId);
        if (projectTeamRepository.existsByTeamId(teamId)) {
            throw new BadRequestException("Cannot delete team while it is assigned to one or more projects");
        }

        teamChatRepository.findByTeamId(teamId).ifPresent(teamChat -> {
            teamChatMessageRepository.deleteByTeamChatId(teamChat.getId());
            teamChatRepository.delete(teamChat);
        });

        teamMemberRepository.deleteByTeamId(teamId);
        teamRepository.delete(team);

        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.TEAM,
                teamId,
                "{\"name\":\"" + escapeJson(team.getName()) + "\"}"
        );
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamResponseDto> listTeams() {
        authorizationService.requirePermission(Permission.VIEW_TEAM);
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        List<Team> teams = role == PlatformRole.EMPLOYEE
                ? findTeamsForEmployee(authorizationService.getCurrentEmployeeIdOrThrow())
                : teamRepository.findAll();
        return toTeamResponses(teams);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamResponseDto> listMyTeams() {
        authorizationService.requirePermission(Permission.VIEW_TEAM);
        Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
        return toTeamResponses(findTeamsForEmployee(currentEmployeeId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public TeamDetailResponseDto getTeamDetails(Long teamId) {
        authorizationService.requirePermission(Permission.VIEW_TEAM);
        Team team = getTeamOrThrow(teamId);
        enforceTeamReadAccess(team);
        List<TeamMemberResponseDto> members = listTeamMembers(teamId);
        List<TeamProjectSummaryDto> assignedProjects = projectTeamRepository.findByTeamId(teamId).stream()
                .map(this::toTeamProjectSummary)
                .toList();

        return TeamDetailResponseDto.builder()
                .team(toTeamResponse(team))
                .members(members)
                .assignedProjects(assignedProjects)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamMemberResponseDto> listTeamMembers(Long teamId) {
        authorizationService.requirePermission(Permission.VIEW_TEAM);
        Team team = getTeamOrThrow(teamId);
        enforceTeamReadAccess(team);

        return teamMemberRepository.findByTeamIdOrderByJoinedAtDesc(teamId)
                .stream()
                .map(this::toTeamMemberResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<TeamResponseDto> listTeamsPaged(
            Long managerId,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.VIEW_TEAM);
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();

        if (role == PlatformRole.EMPLOYEE) {
            Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
            Page<Team> resultPage = teamRepository.searchByEmployeeVisibility(
                    currentEmployeeId,
                    managerId,
                    search,
                    PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
            );

            return PagedResultDto.<TeamResponseDto>builder()
                    .items(toTeamResponses(resultPage.getContent()))
                    .page(resultPage.getNumber())
                    .size(resultPage.getSize())
                    .totalElements(resultPage.getTotalElements())
                    .totalPages(resultPage.getTotalPages())
                    .build();
        }

        Page<Team> resultPage = teamRepository.search(
                managerId,
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<TeamResponseDto>builder()
                .items(toTeamResponses(resultPage.getContent()))
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
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

    private TeamResponseDto toTeamResponse(Team team) {
        long activeMembers = teamMemberRepository.countByTeamIdAndLeftAtIsNull(team.getId());
        return toTeamResponse(team, activeMembers);
    }

    private TeamResponseDto toTeamResponse(Team team, long activeMembers) {
        return TeamResponseDto.builder()
                .id(team.getId())
                .name(team.getName())
                .manager(tenantDtoMapper.toEmployeeSimple(team.getManager()))
                .activeMemberCount(activeMembers)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }

    private TeamMemberResponseDto toTeamMemberResponse(TeamMember teamMember) {
        return TeamMemberResponseDto.builder()
                .id(teamMember.getId())
                .teamId(teamMember.getTeam().getId())
                .employee(tenantDtoMapper.toEmployeeSimple(teamMember.getEmployee()))
                .functionalRole(teamMember.getFunctionalRole())
                .joinedAt(teamMember.getJoinedAt())
                .leftAt(teamMember.getLeftAt())
                .build();
    }

    private TeamProjectSummaryDto toTeamProjectSummary(ProjectTeam projectTeam) {
        return TeamProjectSummaryDto.builder()
                .projectId(projectTeam.getProject().getId())
                .projectName(projectTeam.getProject().getName())
                .projectStatus(projectTeam.getProject().getStatus())
                .startDate(projectTeam.getProject().getStartDate())
                .endDate(projectTeam.getProject().getEndDate())
                .build();
    }

    private List<TeamResponseDto> toTeamResponses(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, Long> activeCounts = getActiveMemberCountsByTeamId(teamIds);

        return teams.stream()
                .map(team -> toTeamResponse(team, activeCounts.getOrDefault(team.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> getActiveMemberCountsByTeamId(List<Long> teamIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : teamMemberRepository.countActiveMembersByTeamIds(teamIds)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private void enforceTeamReadAccess(Team team) {
        if (!authorizationService.canAccessTeam(team)) {
            throw new ForbiddenOperationException("You are not allowed to access this team");
        }
    }

    private boolean isTeamParticipant(Team team, Long employeeId) {
        if (team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            return true;
        }
        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), employeeId).isPresent();
    }

    private List<Team> findTeamsForEmployee(Long employeeId) {
        Set<Long> teamIds = new LinkedHashSet<>();

        teamRepository.findByManagerId(employeeId)
                .stream()
                .map(Team::getId)
                .forEach(teamIds::add);

        teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(employeeId)
                .stream()
                .map(teamMember -> teamMember.getTeam().getId())
                .forEach(teamIds::add);

        if (teamIds.isEmpty()) {
            return List.of();
        }
        return teamRepository.findAllById(teamIds);
    }

    private void ensureManagerMembership(Team team, Employee manager) {
        if (team == null || team.getId() == null || manager == null) {
            return;
        }

        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), manager.getId())
                .orElseGet(() -> {
                    TeamMember created = new TeamMember();
                    created.setTeam(team);
                    created.setEmployee(manager);
                    created.setJoinedAt(LocalDateTime.now());
                    return created;
                });

        if (teamMember.getFunctionalRole() == null || teamMember.getFunctionalRole() == TeamFunctionalRole.MEMBER) {
            teamMember.setFunctionalRole(TeamFunctionalRole.TEAM_LEAD);
        }
        teamMember.setLeftAt(null);
        teamMemberRepository.save(teamMember);
    }

    private String normalizeTeamName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Team name is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "name".equals(sortBy);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void notifyTeamLeaderChange(Team team) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        if (team.getManager() != null) {
            recipientIds.add(team.getManager().getId());
        }
        teamMemberRepository.findByTeamIdAndLeftAtIsNull(team.getId()).stream()
                .map(teamMember -> teamMember.getEmployee().getId())
                .forEach(recipientIds::add);

        for (Long recipientId : recipientIds) {
            Employee recipient = employeeRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                continue;
            }
            emailNotificationService.sendTeamLeaderChangedEmail(
                    recipient.getEmail(),
                    buildFullName(recipient),
                    team.getName(),
                    buildFullName(team.getManager())
            );
        }
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
