package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
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
import java.util.ArrayList;
import java.util.Comparator;
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
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;

    public TeamServiceImpl(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamChatRepository teamChatRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            EmployeeRepository employeeRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamChatRepository = teamChatRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.employeeRepository = employeeRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public TeamResponseDto createTeam(TeamCreateRequestDto requestDto) {
        String teamName = normalizeTeamName(requestDto.getName());
        if (teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        Team team = new Team();
        team.setName(teamName);
        team.setManager(manager);

        Team saved = teamRepository.save(team);
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
        Team team = getTeamOrThrow(teamId);
        String teamName = normalizeTeamName(requestDto.getName());

        if (!team.getName().equalsIgnoreCase(teamName) && teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        team.setName(teamName);
        team.setManager(manager);

        Team updated = teamRepository.save(team);
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
        Team team = getTeamOrThrow(teamId);
        Employee manager = getActiveEmployeeOrThrow(managerEmployeeId);
        team.setManager(manager);
        Team updated = teamRepository.save(team);
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
        Team team = getTeamOrThrow(teamId);
        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Active team membership not found"));

        if (team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            throw new BadRequestException("Cannot remove the current team manager from members. Reassign manager first.");
        }

        teamMember.setLeftAt(LocalDateTime.now());
        teamMemberRepository.save(teamMember);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employeeId + "}"
        );
    }

    @Override
    public void deleteTeam(Long teamId) {
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
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        List<Team> teams = role == PlatformRole.EMPLOYEE
                ? findTeamsForEmployee(resolveCurrentEmployeeOrThrow().getId())
                : teamRepository.findAll();
        return toTeamResponses(teams);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamResponseDto> listMyTeams() {
        Long currentEmployeeId = resolveCurrentEmployeeOrThrow().getId();
        return toTeamResponses(findTeamsForEmployee(currentEmployeeId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public TeamDetailResponseDto getTeamDetails(Long teamId) {
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
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();

        if (role == PlatformRole.EMPLOYEE) {
            Long currentEmployeeId = resolveCurrentEmployeeOrThrow().getId();
            return listTeamsPagedForEmployee(
                    currentEmployeeId,
                    managerId,
                    search,
                    resolvedPage,
                    resolvedSize,
                    resolvedSortBy,
                    direction
            );
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

    private Employee resolveCurrentEmployeeOrThrow() {
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();

        if (principal.getId() != null) {
            Employee employeeByPlatformId = employeeRepository.findByPlatformUserId(principal.getId()).orElse(null);
            if (employeeByPlatformId != null) {
                return employeeByPlatformId;
            }
        }

        String currentEmail = principal.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new ForbiddenOperationException("Current user is not linked to an employee profile");
        }

        return employeeRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new ForbiddenOperationException("Current user is not linked to an employee profile"));
    }

    private void enforceTeamReadAccess(Team team) {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return;
        }

        Long currentEmployeeId = resolveCurrentEmployeeOrThrow().getId();
        if (!isTeamParticipant(team, currentEmployeeId)) {
            throw new ForbiddenOperationException("Employees cannot access this team");
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

    private PagedResultDto<TeamResponseDto> listTeamsPagedForEmployee(
            Long employeeId,
            Long managerId,
            String search,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction) {
        String normalizedSearch = trimToNull(search);

        List<Team> filteredTeams = findTeamsForEmployee(employeeId).stream()
                .filter(team -> managerId == null ||
                        (team.getManager() != null && managerId.equals(team.getManager().getId())))
                .filter(team -> matchesTeamSearch(team, normalizedSearch))
                .toList();

        List<Team> sortedTeams = new ArrayList<>(filteredTeams);
        sortedTeams.sort(teamComparator(sortBy, direction));

        int fromIndex = Math.min(page * size, sortedTeams.size());
        int toIndex = Math.min(fromIndex + size, sortedTeams.size());
        long totalElements = sortedTeams.size();
        int totalPages = (int) Math.ceil(totalElements / (double) size);

        return PagedResultDto.<TeamResponseDto>builder()
                .items(toTeamResponses(sortedTeams.subList(fromIndex, toIndex)))
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    private boolean matchesTeamSearch(Team team, String search) {
        if (search == null) {
            return true;
        }
        String teamName = team.getName();
        return teamName != null && teamName.toLowerCase().contains(search.toLowerCase());
    }

    private Comparator<Team> teamComparator(String sortBy, Sort.Direction direction) {
        Comparator<Team> comparator = switch (sortBy) {
            case "updatedAt" -> Comparator.comparing(
                    Team::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "name" -> Comparator.comparing(
                    Team::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            default -> Comparator.comparing(
                    Team::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        return direction.isAscending() ? comparator : comparator.reversed();
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
}
